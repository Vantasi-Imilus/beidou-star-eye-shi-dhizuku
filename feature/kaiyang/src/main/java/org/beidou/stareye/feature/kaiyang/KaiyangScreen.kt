package org.beidou.stareye.feature.kaiyang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape
import kotlinx.coroutines.launch

/**
 * 开阳中枢 —— Dhizuku（Device Owner 授权）管理界面。
 *
 * 展示设备所有者状态、激活指令、以及各应用的授权开关。
 * 授权逻辑沿用原版 Dhizuku，界面为星空玻璃风格。
 */
@Composable
fun KaiyangScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(KaiyangFeature.star.order)

    var isOwner by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<Pair<KaiyangApp, String>>>(emptyList()) }

    fun refresh() {
        isOwner = KaiyangState.isOwner(context)
        apps = KaiyangStore.all(context).map { entity ->
            val label = try {
                context.packageManager.getPackageNameForUid(entity.uid) ?: "uid=${entity.uid}"
            } catch (_: Throwable) {
                "uid=${entity.uid}"
            }
            entity to label
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // 顶栏
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = beidouShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = KaiyangFeature.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StarWhite,
                )
                Text(
                    text = KaiyangFeature.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StarGlyph(color = accent, size = 44.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = KaiyangFeature.star.chineseName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }

        Spacer(Modifier.height(20.dp))

        // 状态卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = beidouShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusRow(
                "设备所有者",
                if (isOwner) "已激活" else "未激活",
                if (isOwner) accent else Color.Gray,
            )
            StatusRow(
                "中枢状态",
                if (KaiyangStore.isEnabled(context)) "已启用" else "已停用",
                if (KaiyangStore.isEnabled(context)) accent else StarGold,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!isOwner) {
            DeviceOwnerActivationCard(context, onActivated = { refresh() })
        } else {
            AuthorizedAppsCard(
                context = context,
                apps = apps,
                onToggle = { entity, allow ->
                    KaiyangStore.upsert(context, entity.copy(allowApi = allow))
                    refresh()
                },
            )
            Spacer(Modifier.height(16.dp))
            OwnerActionsCard(context = context, onChanged = { refresh() })
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = StarWhite.copy(alpha = 0.55f))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun DeviceOwnerActivationCard(context: Context, onActivated: () -> Unit) {
    val component = "${context.packageName}/${KaiyangDAReceiver::class.java.name}"
    val setCmd = "adb shell dpm set-device-owner $component"
    val removeCmd = "adb shell dpm remove-active-admin $component"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = beidouShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "✦ 激活开阳中枢",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StarBlue,
        )
        Text(
            text = "开阳中枢基于「设备所有者」运行。可用下面两种方式激活（要求设备无任何账户）：",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.8f),
        )

        // 方式一：通过天枢中枢一键激活（用 shell 身份执行 dpm）
        val ready = KaiyangActivator.shizukuReady()
        var activating by remember { mutableStateOf(false) }
        var output by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = beidouShape(16.dp))
                .clickable(enabled = !activating) {
                    activating = true
                    output = ""
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            KaiyangActivator.activateViaShizuku(context) { output += it + "\n" }
                        }
                        kotlinx.coroutines.delay(1200)
                        activating = false
                        if (ok) onActivated()
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (activating) "激活中…" else "⚡ 通过天枢中枢激活",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (ready) StarBlue else StarWhite.copy(alpha = 0.4f),
            )
        }
        if (!ready) {
            Text(
                text = "（需先在「天枢中枢」激活服务并授予本应用权限）",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.45f),
            )
        }
        if (output.isNotEmpty()) {
            Text(
                text = output.trim(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.7f),
            )
        }

        Text(
            text = "方式二 · 电脑终端执行：",
            style = MaterialTheme.typography.labelMedium,
            color = StarGold.copy(alpha = 0.9f),
        )
        CommandBlock(context, "一键激活指令", setCmd)
        Text(
            text = "如需取消激活：",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.5f),
        )
        CommandBlock(context, "取消激活指令", removeCmd)
    }
}

@Composable
private fun AuthorizedAppsCard(
    context: Context,
    apps: List<Pair<KaiyangApp, String>>,
    onToggle: (KaiyangApp, Boolean) -> Unit,
) {
    val accent = starAccent(6)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = beidouShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "✦ 应用授权管理",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        if (apps.isEmpty()) {
            Text(
                text = "暂无应用申请开阳中枢授权",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.45f),
            )
            return@Column
        }
        apps.forEach { (entity, label) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val icon: Bitmap? = try {
                    context.packageManager.getPackageInfoForUid(entity.uid)
                        ?.applicationInfo?.loadIcon(context.packageManager)?.toBitmap(96, 96)
                } catch (_: Throwable) {
                    null
                }
                if (icon != null) {
                    Image(icon.asImageBitmap(), null, modifier = Modifier.size(36.dp))
                } else {
                    StarGlyph(color = accent, size = 28.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StarWhite,
                    )
                    Text(
                        "uid=${entity.uid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StarWhite.copy(alpha = 0.4f),
                    )
                }
                Switch(
                    checked = entity.allowApi,
                    onCheckedChange = { onToggle(entity, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent,
                        checkedTrackColor = accent.copy(alpha = 0.35f),
                        uncheckedThumbColor = StarWhite.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )
            }
        }
    }
}

/**
 * 设备所有者身份操作：转移给其他 admin / 主动解除。
 */
@Composable
private fun OwnerActionsCard(context: Context, onChanged: () -> Unit) {
    var showTransfer by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val targets = remember { KaiyangState.transferTargets(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = beidouShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "✦ 设备所有者身份",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StarGold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(beidouShape(16.dp))
                    .background(StarBlue.copy(alpha = 0.18f))
                    .clickable { showTransfer = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("转移身份", color = StarBlue, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(beidouShape(16.dp))
                    .background(Color(0xFFFF6B9D).copy(alpha = 0.18f))
                    .clickable { showClear = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("解除身份", color = Color(0xFFFF6B9D), fontWeight = FontWeight.Bold)
            }
        }

        if (message.isNotEmpty()) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.75f),
            )
        }
    }

    // 转移对话框
    if (showTransfer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTransfer = false },
            title = { Text("转移设备所有者") },
            text = {
                if (targets.isEmpty()) {
                    Text("未找到可转移的目标。\n\n转移要求目标应用：\n· 已安装且已激活为设备管理员\n· 与开阳中枢不同包\n· 其 device-admin 配置声明了 support-transfer-ownership")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("选择接收设备所有者身份的应用：")
                        targets.forEach { (cn, label) ->
                            Text(
                                text = "· $label (${cn.packageName})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val r = KaiyangState.transferOwnership(context, cn)
                                        message = r.fold(
                                            onSuccess = { "✅ 已转移给 $label" },
                                            onFailure = { "转移失败：${it.message}" },
                                        )
                                        showTransfer = false
                                        onChanged()
                                    }
                                    .padding(vertical = 8.dp),
                                color = StarBlue,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showTransfer = false }) { Text("关闭") }
            },
        )
    }

    // 解除对话框
    if (showClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("解除设备所有者") },
            text = { Text("解除后开阳中枢将失去设备所有者权限，且此操作不可撤销（需重新通过 adb 激活）。确定解除？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val r = KaiyangState.clearOwner(context)
                    message = r.fold(
                        onSuccess = { "✅ 已解除设备所有者身份" },
                        onFailure = { "解除失败：${it.message}" },
                    )
                    showClear = false
                    onChanged()
                }) { Text("确认解除", color = Color(0xFFFF6B9D)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CommandBlock(context: Context, title: String, command: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = StarGold.copy(alpha = 0.9f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(beidouShape(14.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(beidouShape(50))
                    .background(StarBlue.copy(alpha = 0.18f))
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("beidou", command))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "复制",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StarBlue,
                )
            }
        }
    }
}
