package org.beidou.stareye.feature.tianxuan

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.core.freeze.FreezeState
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.components.rememberFreezeRevision
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 天璇中枢 —— App Manager（应用管理）界面。
 *
 * 方案A：应用列表/详情、冻结解冻、强停、清数据、卸载、导出 APK、操作记录。
 * 界面统一为星空液态玻璃风格。
 */
@Composable
fun TianxuanScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<TianxuanApp?>(null) }
    var componentApp by remember { mutableStateOf<TianxuanApp?>(null) }
    var showResidue by remember { mutableStateOf(false) }
    var editorApp by remember { mutableStateOf<TianxuanApp?>(null) }

    when {
        editorApp != null -> TianxuanApkEditorScreen(
            app = editorApp!!,
            onBack = { editorApp = null },
        )

        showResidue -> TianxuanResidueScreen(onBack = { showResidue = false })

        componentApp != null -> TianxuanComponentScreen(
            app = componentApp!!,
            onBack = { componentApp = null },
        )

        selected != null -> AppDetailScreen(
            app = selected!!,
            onBack = { selected = null },
            onOpenComponents = { componentApp = it },
            onOpenEditor = { editorApp = it },
        )

        else -> AppListScreen(
            onBack = onBack,
            onOpen = { selected = it },
            onOpenResidue = { showResidue = true },
        )
    }
}

// ───────────────────────── 应用列表 ─────────────────────────

@Composable
private fun AppListScreen(onBack: () -> Unit, onOpen: (TianxuanApp) -> Unit, onOpenResidue: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(TianxuanFeature.star.order)

    var apps by remember { mutableStateOf<List<TianxuanApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var shizukuReady by remember { mutableStateOf(false) }
    var isOwner by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(filter, refreshKey) {
        loading = true
        apps = withContext(Dispatchers.IO) { TianxuanManager.listApps(context, filter) }
        shizukuReady = TianxuanManager.shizukuReady()
        isOwner = TianxuanManager.isDeviceOwner(context)
        loading = false
    }

    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

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
                Text(TianxuanFeature.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    TianxuanFeature.subtitle,
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
                    .clickable(enabled = !loading) { refreshKey++ }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "刷新",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (loading) StarWhite.copy(alpha = 0.4f) else accent,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable { showLog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("操作记录", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(StarGold.copy(alpha = 0.14f))
                    .clickable { onOpenResidue() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("残留扫描", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = StarGold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 能力状态
        Column(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CapabilityRow("天枢中枢（Shizuku）", if (shizukuReady) "已就绪" else "未就绪", if (shizukuReady) StarBlue else Color.Gray)
            CapabilityRow("开阳中枢（设备所有者）", if (isOwner) "已激活" else "未激活", if (isOwner) accent else Color.Gray)
        }

        Spacer(Modifier.height(12.dp))

        // 搜索
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(16.dp)),
            placeholder = { Text("搜索应用名或包名…", color = StarWhite.copy(alpha = 0.35f)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = accent,
                focusedTextColor = StarWhite,
                unfocusedTextColor = StarWhite,
            ),
        )

        Spacer(Modifier.height(12.dp))

        // 过滤
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip("全部", filter == AppFilter.ALL, accent) { filter = AppFilter.ALL }
            FilterChip("用户应用", filter == AppFilter.USER, accent) { filter = AppFilter.USER }
            FilterChip("系统应用", filter == AppFilter.SYSTEM, accent) { filter = AppFilter.SYSTEM }
        }

        Spacer(Modifier.height(12.dp))

        // 列表
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在加载应用…", color = StarWhite.copy(alpha = 0.6f))
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无匹配应用", color = StarWhite.copy(alpha = 0.5f))
            }
        } else {
            if (apps.size <= 1) {
                Column(
                    modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("✦ 只看到本应用？", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StarGold)
                    Text(
                        "系统可能尚未授予「读取已安装应用列表」权限。请在系统设置中允许后，点右上角「刷新」重新加载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = StarWhite.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    AppRow(app = app, accent = accent, onClick = { onOpen(app) })
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showLog) {
        OperationLogDialog(onDismiss = { showLog = false })
    }
}

@Composable
private fun CapabilityRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = StarWhite.copy(alpha = 0.6f))
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else StarWhite.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun AppRow(app: TianxuanApp, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, accent, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = StarWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(app.versionName ?: "—", style = MaterialTheme.typography.labelMedium, color = StarWhite.copy(alpha = 0.6f))
            Text(
                if (app.isSystem) "系统" else "用户",
                style = MaterialTheme.typography.labelSmall,
                color = if (app.isSystem) StarGold.copy(alpha = 0.8f) else accent,
            )
        }
    }
}

@Composable
private fun AppIcon(bitmap: Bitmap?, accent: Color, size: androidx.compose.ui.unit.Dp) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 3)),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            StarGlyph(color = accent, size = size / 2)
        }
    }
}

// ───────────────────────── 应用详情 ─────────────────────────

@Composable
private fun AppDetailScreen(
    app: TianxuanApp,
    onBack: () -> Unit,
    onOpenComponents: (TianxuanApp) -> Unit,
    onOpenEditor: (TianxuanApp) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianxuanFeature.star.order)

    var frozen by remember { mutableStateOf(false) }
    var freezeLabel by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    // 签名信息（懒加载：展开后读取并缓存）
    var sigExpanded by remember { mutableStateOf(false) }
    var sigList by remember { mutableStateOf<List<TianxuanSignature>?>(null) }
    var sigLoading by remember { mutableStateOf(false) }
    LaunchedEffect(sigExpanded) {
        if (sigExpanded && sigList == null) {
            sigLoading = true
            sigList = withContext(Dispatchers.IO) { TianxuanManager.signatureInfo(context, app.packageName) }
            sigLoading = false
        }
    }

    // 订阅跨中枢冻结变更：瑶光 / 玉衡改动后此处自动重读
    val freezeRevision = rememberFreezeRevision()

    LaunchedEffect(app.packageName, freezeRevision) {
        val flags = withContext(Dispatchers.IO) { FreezeState.flags(context, app.packageName) }
        frozen = flags.frozen
        freezeLabel = flags.kind.label
    }

    fun record(action: String, ok: Boolean, detail: String) {
        TianxuanLog.add(context, TianxuanLogEntry(System.currentTimeMillis(), action, app.packageName, app.label, ok, detail))
    }

    fun runAction(name: String, block: () -> Result<*>) {
        scope.launch {
            busy = name
            message = ""
            val r = withContext(Dispatchers.IO) { block() }
            busy = null
            val ok = r.isSuccess
            val exportAction = name == "导出APK" || name == "导出APKs"
            val detail = if (ok) {
                (r.getOrNull() as? String)?.takeIf { it.isNotBlank() && exportAction } ?: "成功"
            } else {
                "失败：${r.exceptionOrNull()?.message ?: "未知错误"}"
            }
            message = if (exportAction && ok) "已导出：$detail" else detail
            record(name, ok, detail)
            if (name == "冻结" || name == "解冻") {
                frozen = withContext(Dispatchers.IO) { TianxuanManager.isFrozen(context, app.packageName) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // 顶栏
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Text(TianxuanFeature.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
        }

        Spacer(Modifier.height(20.dp))

        // 应用头
        Row(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(24.dp)).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon, accent, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = StarWhite.copy(alpha = 0.55f))
                Spacer(Modifier.height(6.dp))
                Text(
                    "版本 ${app.versionName ?: "—"} · ${if (app.isSystem) "系统应用" else "用户应用"}" +
                        if (frozen) " · $freezeLabel" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (frozen) StarGold else accent,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 操作按钮
        Column(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("✦ 应用操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("强停", StarBlue, Modifier.weight(1f), busy == "强停") { runAction("强停") { TianxuanManager.forceStop(app.packageName) } }
                ActionButton(if (frozen) "解冻" else "冻结", if (frozen) StarGold else Color(0xFFFF6B9D), Modifier.weight(1f), busy == "冻结" || busy == "解冻") {
                    runAction(if (frozen) "解冻" else "冻结") {
                        if (frozen) TianxuanManager.unfreeze(context, app.packageName)
                        else TianxuanManager.freeze(context, app.packageName)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("清数据", StarGold, Modifier.weight(1f), busy == "清数据") { pendingAction = "清数据" }
                ActionButton("卸载", Color(0xFFFF6B9D), Modifier.weight(1f), busy == "卸载") { pendingAction = "卸载" }
            }
            ActionButton("导出 APK", accent, Modifier.fillMaxWidth(), busy == "导出APK") {
                runAction("导出APK") { TianxuanManager.exportApk(context, app) }
            }
            ActionButton("导出全部（.apks）", accent, Modifier.fillMaxWidth(), busy == "导出APKs") {
                runAction("导出APKs") { TianxuanManager.exportAllApks(context, app) }
            }
            ActionButton("APK 编辑", StarGold, Modifier.fillMaxWidth(), false) {
                onOpenEditor(app)
            }
            ActionButton("组件管理", StarBlue, Modifier.fillMaxWidth(), false) {
                onOpenComponents(app)
            }

            if (busy != null) {
                Text("正在执行「$busy」…", style = MaterialTheme.typography.labelMedium, color = StarWhite.copy(alpha = 0.7f))
            }
            if (message.isNotEmpty()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWhite.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 详情信息
        Column(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("✦ 应用详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StarGold)
            InfoRow("版本号", "${app.versionName ?: "—"} (${app.versionCode})")
            InfoRow("UID", app.uid.toString())
            InfoRow("目标 SDK", app.targetSdk.toString())
            InfoRow("安装时间", formatTime(app.installTime))
            InfoRow("更新时间", formatTime(app.updateTime))
            InfoRow("APK 大小", app.sourceDir?.let { TianxuanManager.apkSizeBytes(app)?.let { formatBytes(it) } } ?: "—")
            InfoRow("APK 路径", app.sourceDir ?: "—")
            InfoRow("数据目录", app.dataDir ?: "—")
        }

        Spacer(Modifier.height(16.dp))

        // 签名信息（懒加载）
        Column(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("✦ 签名信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StarGold)
            Text(
                "APK 的签名证书用于校验应用身份与安装来源，展开后从系统签名信息中读取。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.5f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable { sigExpanded = !sigExpanded }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    if (sigExpanded) "收起签名信息" else "展开签名信息",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            if (sigExpanded) {
                when {
                    sigLoading -> Text("正在读取签名…", style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.6f))
                    sigList.isNullOrEmpty() -> Text(
                        "未获取到签名信息（可能为系统预装或未签名应用）",
                        style = MaterialTheme.typography.bodySmall,
                        color = StarWhite.copy(alpha = 0.6f),
                    )

                    else -> {
                        val list = sigList!!
                        Text(
                            "共 ${list.size} 个签名者",
                            style = MaterialTheme.typography.labelSmall,
                            color = StarWhite.copy(alpha = 0.5f),
                        )
                        list.forEachIndexed { index, sig ->
                            SignerBlock(index + 1, list.size, sig, accent)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // 确认对话框
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pendingAction!!) },
            text = {
                Text(
                    if (pendingAction == "卸载") "确定卸载「${app.label}」吗？系统应用将回退到出厂版本。"
                    else "确定清除「${app.label}」的全部数据吗？此操作不可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingAction!!
                    pendingAction = null
                    runAction(action) {
                        if (action == "卸载") TianxuanManager.uninstall(app.packageName)
                        else TianxuanManager.clearData(app.packageName)
                    }
                }) { Text("确认", color = Color(0xFFFF6B9D)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ActionButton(label: String, color: Color, modifier: Modifier, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (busy) StarWhite.copy(alpha = 0.4f) else color,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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

// ───────────────────────── 签名信息 ─────────────────────────

/** 单个签名者的信息卡（多签名者时带序号） */
@Composable
private fun SignerBlock(index: Int, total: Int, sig: TianxuanSignature, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (total > 1) {
            Text(
                "签名者 #$index",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        SigInfoRow("签名者", sig.signerDn)
        SigInfoRow("颁发者", sig.issuerDn)
        SigInfoRow("SHA-256", sig.sha256)
        SigInfoRow("SHA-1", sig.sha1)
        SigInfoRow("有效期", "${formatTime(sig.validFrom)} 至 ${formatTime(sig.validUntil)}")
        SigInfoRow("序列号", sig.serialNumber)
    }
}

@Composable
private fun SigInfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.45f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = StarWhite.copy(alpha = 0.85f),
        )
    }
}

// ───────────────────────── 操作记录 ─────────────────────────

@Composable
private fun OperationLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(TianxuanFeature.star.order)
    val entries = remember { TianxuanLog.all(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作记录") },
        text = {
            if (entries.isEmpty()) {
                Text("暂无操作记录", color = StarWhite.copy(alpha = 0.5f))
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = {
                TianxuanLog.clear(context)
                Toast.makeText(context, "已清空操作记录", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("清空", color = Color(0xFFFF6B9D)) }
        },
    )
}

// ───────────────────────── 工具 ─────────────────────────

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatTime(millis: Long): String =
    if (millis <= 0) "—" else timeFormat.format(Date(millis))
