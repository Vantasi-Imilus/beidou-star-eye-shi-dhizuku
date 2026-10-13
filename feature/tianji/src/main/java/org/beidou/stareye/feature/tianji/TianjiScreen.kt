package org.beidou.stareye.feature.tianji

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.core.shell.ShizukuShell
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 天玑中枢 —— InstallerX（现代化安装器）界面。
 *
 * 三个标签页：安装（选包+配置+分包）/ 卸载（应用列表）/ 设置（授权器与安装策略）。
 * 界面统一为星空液态玻璃风格。
 */
@Composable
fun TianjiScreen(onBack: () -> Unit) {
    val accent = starAccent(TianjiFeature.star.order)
    var tab by remember { mutableStateOf(0) }
    var showLog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(TianjiFeature.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    TianjiFeature.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable { showLog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("记录", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 标签切换
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabChip("安装", tab == 0, accent) { tab = 0 }
            TabChip("卸载", tab == 1, accent) { tab = 1 }
            TabChip("设置", tab == 2, accent) { tab = 2 }
        }

        Spacer(Modifier.height(14.dp))

        // 用 weight 限定为「剩余空间」，避免子页 fillMaxSize 请求整屏高度而被父 Column 截断
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> TianjiInstallTab(accent = accent)
                1 -> TianjiUninstallTab(accent = accent)
                else -> TianjiSettingsTab(accent = accent)
            }
        }
    }

    if (showLog) {
        TianjiLogDialog(onDismiss = { showLog = false })
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else StarWhite.copy(alpha = 0.6f),
        )
    }
}

// ───────────────────────── 安装标签页 ─────────────────────────

@Composable
private fun TianjiInstallTab(accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apk by remember { mutableStateOf<TianjiApk?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var selectedSplits by remember { mutableStateOf<Set<String>>(emptySet()) }

    var privilegedReady by remember { mutableStateOf(false) }
    var channelLabel by remember { mutableStateOf("") }
    var isDefaultInstaller by remember { mutableStateOf(false) }
    var isDefaultUninstaller by remember { mutableStateOf(false) }
    var ownerReady by remember { mutableStateOf(false) }
    var rivalExists by remember { mutableStateOf(false) }
    var defaultMsg by remember { mutableStateOf("") }
    var showBiometricDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        val options = TianjiPrefs.options(context)
        privilegedReady = TianjiPrivileged.ready(context, options.authorizer)
        channelLabel = TianjiPrivileged.describe(context, options.authorizer)
        isDefaultInstaller = TianjiDefaultInstaller.isDefault(context)
        isDefaultUninstaller = TianjiDefaultInstaller.isUninstallerDefault(context)
        ownerReady = TianjiDefaultInstaller.ownerReady(context)
        rivalExists = TianjiDefaultInstaller.hasRival(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            parsing = true
            message = ""
            stage = ""
            val result = withContext(Dispatchers.IO) {
                runCatching { TianjiAnalyzer.analyze(context, copyToCache(context, uri)).getOrThrow() }
            }
            parsing = false
            result.fold(
                onSuccess = { info ->
                    apk = info
                    // 分包默认选中策略来自设置
                    val all = info.splitFiles.map { it.absolutePath }.toSet()
                    selectedSplits = if (TianjiPrefs.options(context).selectAllSplits) all else emptySet()
                },
                onFailure = {
                    apk = null
                    message = "解析失败：${it.message ?: "未知错误"}"
                },
            )
        }
    }

    fun doInstall() {
        val info = apk ?: return
        val splits = info.splitFiles.filter { it.absolutePath in selectedSplits }
        installing = true
        message = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TianjiInstaller.install(context, info, splits) { s -> stage = s }
            }
            installing = false
            stage = ""
            val detail = result.fold(
                onSuccess = { it.ifBlank { "成功" } },
                onFailure = { "失败：${it.message ?: "未知错误"}" },
            )
            message = detail
            TianjiLog.add(
                context,
                TianjiLogEntry(System.currentTimeMillis(), "安装", info.packageName, info.label, result.isSuccess, detail),
            )
            if (result.isSuccess) {
                apk = withContext(Dispatchers.IO) { TianjiAnalyzer.analyze(context, info.file).getOrNull() } ?: info
                refreshKey++
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 通道状态
        Column(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusRow(
                "特权通道",
                if (privilegedReady) "$channelLabel · 静默安装" else "不可用 · 系统安装器",
                if (privilegedReady) StarBlue else StarGold,
            )
            StatusRow("安装原因", TianjiPrefs.options(context).installReason.label, StarWhite.copy(alpha = 0.75f))
        }

        Spacer(Modifier.height(14.dp))

        DefaultInstallerCard(
            isDefault = isDefaultInstaller,
            isUninstallerDefault = isDefaultUninstaller,
            ownerReady = ownerReady,
            rivalExists = rivalExists,
            message = defaultMsg,
            accent = accent,
            onRegister = {
                scope.launch {
                    defaultMsg = ""
                    val r = withContext(Dispatchers.IO) { TianjiDefaultInstaller.register(context) }
                    defaultMsg = r.fold(
                        onSuccess = { "✅ 已注册为默认安装器与卸载器" },
                        onFailure = { "注册失败：${it.message ?: "未知错误"}" },
                    )
                    refreshKey++
                }
            },
            onUnregister = {
                scope.launch {
                    defaultMsg = ""
                    val r = withContext(Dispatchers.IO) { TianjiDefaultInstaller.unregister(context) }
                    defaultMsg = r.fold(
                        onSuccess = { "已取消注册" },
                        onFailure = { "取消失败：${it.message ?: "未知错误"}" },
                    )
                    refreshKey++
                }
            },
            onManual = {
                TianjiDefaultInstaller.openSystemChooser(context)
                defaultMsg = "请在系统弹窗中选择「北斗·群星之眼」并勾选「始终」"
            },
        )

        Spacer(Modifier.height(14.dp))

        // 选择安装包
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(20.dp))
                .clickable(enabled = !parsing && !installing) { picker.launch(arrayOf("*/*")) }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (parsing) "解析中…" else "＋ 选择安装包（APK / APKS / XAPK）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (parsing) StarWhite.copy(alpha = 0.4f) else accent,
            )
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
        }

        apk?.let { info ->
            Spacer(Modifier.height(14.dp))
            TianjiApkCard(apk = info, accent = accent)

            // 分包选择
            if (info.splitFiles.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SplitSelector(
                    splits = info.splitFiles,
                    selected = selectedSplits,
                    accent = accent,
                    onToggle = { path, checked ->
                        selectedSplits = if (checked) selectedSplits + path else selectedSplits - path
                    },
                    onSelectAll = { selectedSplits = info.splitFiles.map { it.absolutePath }.toSet() },
                    onClear = { selectedSplits = emptySet() },
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(20.dp))
                    .clickable(enabled = !installing && TianjiPrefs.isBlocked(context, info.packageName).not()) {
                        if (TianjiPrefs.options(context).requireBiometrics) {
                            showBiometricDialog = true
                        } else {
                            doInstall()
                        }
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        installing -> stage.ifBlank { "安装中…" }
                        TianjiPrefs.isBlocked(context, info.packageName) -> "该应用已被禁止安装"
                        info.installed -> "覆盖安装 ${info.label}"
                        else -> "安装 ${info.label}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        installing -> StarWhite.copy(alpha = 0.45f)
                        TianjiPrefs.isBlocked(context, info.packageName) -> Color(0xFFFF6B9D)
                        else -> StarGold
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // 生物认证门禁
    if (showBiometricDialog) {
        val activity = context as? Activity
        val required = apk?.let { TianjiPrefs.options(context).requireBiometrics } ?: false
        if (activity != null && required) {
            LaunchedEffect(showBiometricDialog) {
                val shown = TianjiBiometric.authenticate(
                    activity,
                    subtitle = "验证身份后安装 ${apk?.label ?: ""}",
                ) { ok ->
                    showBiometricDialog = false
                    if (ok) doInstall() else message = "生物认证未通过，已取消安装"
                }
                if (!shown) {
                    showBiometricDialog = false
                    message = "设备不支持生物认证，已跳过校验并继续安装"
                    doInstall()
                }
            }
        } else {
            LaunchedEffect(showBiometricDialog) {
                showBiometricDialog = false
                doInstall()
            }
        }
    }
}

/**
 * 分包（split）选择器。
 */
@Composable
private fun SplitSelector(
    splits: List<File>,
    selected: Set<String>,
    accent: Color,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ 分包选择（${selected.size}/${splits.size}）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.weight(1f))
            Text(
                "全选",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSelectAll).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "清空",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.6f),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        splits.forEach { f ->
            val checked = f.absolutePath in selected
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle(f.absolutePath, it) },
                    colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = StarWhite.copy(alpha = 0.5f)),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        f.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = StarWhite.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatBytes(f.length()), style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.45f))
                }
            }
        }
    }
}

/**
 * 「默认安装器 / 卸载器」身份卡片。
 */
@Composable
private fun DefaultInstallerCard(
    isDefault: Boolean,
    isUninstallerDefault: Boolean,
    ownerReady: Boolean,
    rivalExists: Boolean,
    message: String,
    accent: Color,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ 默认安装器身份", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    isDefault && isUninstallerDefault -> "安装+卸载 已就位"
                    isDefault -> "安装已就位"
                    else -> "未注册"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDefault) accent else StarGold,
            )
        }

        Text(
            text = when {
                isDefault && isUninstallerDefault ->
                    "打开 APK 与卸载请求都会直接交由天玑中枢处理，不再弹出系统选择框。"
                isDefault ->
                    "安装请求已由本应用接管；卸载请求仍会弹出系统选择框。"
                ownerReady ->
                    "当前安装/卸载请求会弹出系统选择框（共存 ${if (rivalExists) "多个" else "无"}竞争应用）。可一键注册。"
                else ->
                    "一键注册需先激活「开阳中枢」（设备所有者）；也可在系统弹窗中手动选择并勾选「始终」。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = StarWhite.copy(alpha = 0.7f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isDefault && isUninstallerDefault) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFF6B9D).copy(alpha = 0.16f))
                        .clickable(onClick = onUnregister)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消注册", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B9D))
                }
            } else if (ownerReady) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .clickable(onClick = onRegister)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("一键注册为默认安装/卸载器", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(onClick = onManual)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("手动指定…", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
                }
            }
        }

        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.8f))
        }
    }
}

/**
 * 安装包信息卡片（安装页与转接层 Activity 共用）。
 */
@Composable
fun TianjiApkCard(apk: TianjiApk, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (apk.icon != null) {
                Image(
                    bitmap = apk.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    StarGlyph(color = accent, size = 28.dp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    apk.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StarWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    apk.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = StarWhite.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "版本 ${apk.versionName ?: "—"} (${apk.versionCode})" +
                        if (apk.isArchive) " · ${apk.splitFiles.size + 1} 分片" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
        }

        if (apk.signatureMismatch) {
            WarningRow("签名与已安装版本不一致，覆盖安装会失败（需先卸载）", Color(0xFFFF6B9D))
        } else if (apk.versionChange == "降级") {
            WarningRow("目标版本低于已安装版本（降级安装）", StarGold)
        } else if (apk.installed) {
            WarningRow(
                "设备已安装${apk.installedVersionName?.let { " v$it" } ?: ""} · ${apk.versionChange ?: "覆盖安装"}",
                StarBlue,
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        InfoRow("文件大小", formatBytes(apk.totalBytes))
        InfoRow("最低 SDK", apk.minSdk.toString())
        InfoRow("目标 SDK", apk.targetSdk.toString())
        InfoRow("权限数量", apk.permissions.size.toString())
        if (apk.permissions.isNotEmpty()) {
            Text(
                apk.permissions.take(6).joinToString("\n") { "· " + it.substringAfterLast('.') } +
                    if (apk.permissions.size > 6) "\n… 等 ${apk.permissions.size} 项" else "",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun WarningRow(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = StarWhite.copy(alpha = 0.6f))
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.5f), modifier = Modifier.width(84.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = StarWhite.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TianjiLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(TianjiFeature.star.order)
    val entries = remember { TianjiLog.all(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装 / 卸载记录") },
        text = {
            if (entries.isEmpty()) {
                Text("暂无记录", color = StarWhite.copy(alpha = 0.5f))
            } else {
                Column(
                    modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entries.asReversed().forEach { e ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).padding(10.dp),
                        ) {
                            Row {
                                Text(
                                    e.action,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (e.success) accent else Color(0xFFFF6B9D),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(e.time), style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.4f))
                            }
                            Text(
                                e.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = StarWhite.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                e.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = StarWhite.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(onClick = {
                TianjiLog.clear(context)
                Toast.makeText(context, "已清空记录", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("清空", color = Color(0xFFFF6B9D)) }
        },
    )
}

// ───────────────────────── 工具 ─────────────────────────

/**
 * 把外部安装包复制到 cache，供解析与安装使用。
 *
 * `content://` 走 ContentResolver（SAF 已授予读权限）；
 * `file://` 先尝试直接读取（旧系统或已授权），失败则**经天枢 shell 流式读取**——
 * Android 11+ 分区存储下 `READ_EXTERNAL_STORAGE` 不再授予任意文件读取权，
 * 而 shell 具备 /sdcard 读权限，借此兜底可免去申请 MANAGE_EXTERNAL_STORAGE。
 */
internal fun copyToCache(context: Context, uri: Uri): File {
    val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    val dir = File(context.cacheDir, "tianji_input").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "package.apk"
    val safe = (name ?: fallbackName).replace(Regex("[^A-Za-z0-9._-]"), "_")
    val out = File(dir, safe)

    if (uri.scheme == "file") {
        val path = uri.path ?: error("无效的文件路径")
        val direct = runCatching {
            File(path).inputStream().use { ins -> out.outputStream().use { ins.copyTo(it) } }
        }
        if (direct.isSuccess) return out

        val process = ShizukuShell.start("cat ${shellQuote(path)}")
        process.inputStream.use { ins -> out.outputStream().use { ins.copyTo(it, 128 * 1024) } }
        process.errorStream.readBytes()
        process.waitFor()
        check(process.exitValue() == 0) { "无法读取文件（且天枢中枢不可用）：$path" }
        return out
    }

    context.contentResolver.openInputStream(uri)?.use { ins ->
        out.outputStream().use { ins.copyTo(it) }
    } ?: error("无法读取所选文件")
    return out
}

internal fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

internal val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

internal fun formatTime(millis: Long): String =
    if (millis <= 0) "—" else timeFormat.format(Date(millis))

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}
