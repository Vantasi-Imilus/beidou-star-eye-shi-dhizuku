package org.beidou.stareye.feature.tianji

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.background.StarField
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.BeidouTheme
import org.beidou.stareye.ui.theme.DeepSpace
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

/**
 * 天玑中枢 —— 转接层 Activity。
 *
 * 接收第三方应用 / 文件管理器发出的标准安装、卸载 intent，由天玑中枢接管：
 *  · `ACTION_VIEW` / `ACTION_INSTALL_PACKAGE` → 解析 APK 并安装
 *  · `ACTION_SEND` / `ACTION_SEND_MULTIPLE`   → 同上（分享安装包）
 *  · `ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE` → 卸载指定包
 *
 * 这类请求在第三方难以硬编码本应用包名，故以标准契约声明（见 manifest），
 * 使其被系统正确路由到本应用。
 */
class TianjiInstallActivity : ComponentActivity() {

    private var incomingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingIntent = intent
        setContent {
            BeidouTheme {
                Surface(Modifier.fillMaxSize(), color = DeepSpace) {
                    Box(Modifier.fillMaxSize()) {
                        StarField(Modifier.fillMaxSize())
                        val current = incomingIntent
                        if (current != null) {
                            RelayPanel(intent = current, onDone = { finish() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingIntent = intent
    }
}

private sealed interface RelayTarget {
    /** 安装请求：待安装文件的 Uri */
    data class Install(val uri: Uri) : RelayTarget

    /** 卸载请求：目标包名 */
    data class Uninstall(val packageName: String) : RelayTarget

    /** 批量安装请求：多个待安装文件的 Uri */
    data class Batch(val uris: List<Uri>) : RelayTarget

    data object Invalid : RelayTarget
}

private fun parseTarget(intent: Intent): RelayTarget = when (intent.action) {
    Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE ->
        intent.data?.let { RelayTarget.Install(it) } ?: RelayTarget.Invalid

    Intent.ACTION_SEND ->
        @Suppress("DEPRECATION")
        (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            ?.let { RelayTarget.Install(it) } ?: RelayTarget.Invalid

    Intent.ACTION_SEND_MULTIPLE -> {
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?.filterNotNull()
            .orEmpty()
        when (uris.size) {
            0 -> RelayTarget.Invalid
            1 -> RelayTarget.Install(uris.first())
            else -> RelayTarget.Batch(uris)
        }
    }

    Intent.ACTION_DELETE, Intent.ACTION_UNINSTALL_PACKAGE -> {
        val pkg = intent.data?.schemeSpecificPart
        if (pkg.isNullOrBlank()) RelayTarget.Invalid else RelayTarget.Uninstall(pkg)
    }

    else -> RelayTarget.Invalid
}

@Composable
private fun RelayPanel(intent: Intent, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianjiFeature.star.order)
    val target = remember(intent) { parseTarget(intent) }

    // 批量请求：整页交给批量队列页面（单包 / 卸载请求仍走下方既有流程）
    if (target is RelayTarget.Batch) {
        TianjiBatchScreen(uris = target.uris, onBack = onDone)
        return
    }

    var apk by remember(intent) { mutableStateOf<TianjiApk?>(null) }
    var parsing by remember(intent) { mutableStateOf(target is RelayTarget.Install) }
    var busy by remember(intent) { mutableStateOf(false) }
    var message by remember(intent) { mutableStateOf("") }

    LaunchedEffect(intent) {
        if (target is RelayTarget.Install) {
            parsing = true
            apk = withContext(Dispatchers.IO) {
                runCatching {
                    TianjiAnalyzer.analyze(context, copyToCache(context, target.uri)).getOrThrow()
                }.getOrElse {
                    message = "解析失败：${it.message ?: "未知错误"}"
                    null
                }
            }
            parsing = false
        }
    }

    fun record(action: String, pkg: String, label: String, ok: Boolean, detail: String) {
        TianjiLog.add(
            context,
            TianjiLogEntry(System.currentTimeMillis(), action, pkg, label, ok, detail),
        )
    }

    fun runInstall(info: TianjiApk) {
        busy = true
        message = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { TianjiInstaller.install(context, info) }
            busy = false
            val detail = result.fold(
                onSuccess = { it.ifBlank { "成功" } },
                onFailure = { "失败：${it.message ?: "未知错误"}" },
            )
            message = detail
            record("安装", info.packageName, info.label, result.isSuccess, detail)
        }
    }

    /** 按设置决定是否先做生物认证门禁 */
    fun startInstall(info: TianjiApk) {
        val activity = context as? Activity
        if (TianjiPrefs.options(context).requireBiometrics && activity != null) {
            val shown = TianjiBiometric.authenticate(activity, subtitle = "验证身份后安装 ${info.label}") { ok ->
                if (ok) runInstall(info) else message = "生物认证未通过，已取消安装"
            }
            if (!shown) {
                message = "设备不支持生物认证，已跳过校验并继续安装"
                runInstall(info)
            }
        } else {
            runInstall(info)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StarGlyph(color = accent, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("天玑中枢 · 安装请求", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StarWhite)
                Text("由其他应用发起，经转接层路由至此", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(16.dp))

        when (target) {
            is RelayTarget.Install -> {
                if (parsing) {
                    HintCard("正在解析安装包…", accent)
                } else apk?.let { info ->
                    TianjiApkCard(apk = info, accent = accent)
                    Spacer(Modifier.height(16.dp))
                    ActionRow(
                        primary = "安装 ${info.label}",
                        primaryColor = StarGold,
                        enabled = !busy,
                        busy = busy,
                        onPrimary = { startInstall(info) },
                        onCancel = onDone,
                    )
                } ?: HintCard(message.ifBlank { "无法识别该安装包" }, Color(0xFFFF6B9D))
            }

            is RelayTarget.Uninstall -> {
                Column(
                    modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(24.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("卸载请求", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                    Text(target.packageName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = StarWhite)
                    Text(
                        "将经天枢中枢静默卸载；若天枢未就绪则交由系统卸载器处理。",
                        style = MaterialTheme.typography.labelSmall,
                        color = StarWhite.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ActionRow(
                    primary = "卸载",
                    primaryColor = Color(0xFFFF6B9D),
                    enabled = !busy,
                    busy = busy,
                    onPrimary = {
                        busy = true
                        message = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                TianjiInstaller.uninstall(context, target.packageName)
                            }
                            busy = false
                            val detail = result.fold(
                                onSuccess = { it },
                                onFailure = { "失败：${it.message ?: "未知错误"}" },
                            )
                            message = detail
                            record("卸载", target.packageName, target.packageName, result.isSuccess, detail)
                        }
                    },
                    onCancel = onDone,
                )
            }

            RelayTarget.Invalid -> HintCard("无法识别的请求", Color(0xFFFF6B9D))
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun HintCard(text: String, color: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(20.dp)).padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun ActionRow(
    primary: String,
    primaryColor: Color,
    enabled: Boolean,
    busy: Boolean,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(beidouShape(16.dp))
                .background(primaryColor.copy(alpha = 0.18f))
                .clickable(enabled = enabled, onClick = onPrimary)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy) "执行中…" else primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (busy) StarWhite.copy(alpha = 0.4f) else primaryColor,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(beidouShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(enabled = !busy, onClick = onCancel)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("关闭", style = MaterialTheme.typography.titleSmall, color = StarWhite.copy(alpha = 0.7f))
        }
    }
}
