package org.beidou.stareye.feature.tianji

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.material3.AlertDialog
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
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.beidouShape

/** 卸载页的条目 */
data class UninstallEntry(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val icon: Bitmap?,
)

/**
 * 天玑中枢 —— 卸载标签页：列出设备应用（用户/系统可切换 + 搜索），点击即卸载。
 */
@Composable
fun TianjiUninstallTab(accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<UninstallEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<UninstallEntry?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(showSystem, refreshKey) {
        loading = true
        apps = withContext(Dispatchers.IO) { listUninstallable(context, showSystem) }
        loading = false
    }

    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showSystem) "系统应用（卸载将回退出厂版本）" else "用户应用",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (showSystem) "只看用户" else "含系统",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier
                    .clip(beidouShape(50))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable { showSystem = !showSystem }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(16.dp)),
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

        Spacer(Modifier.height(10.dp))

        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在加载应用…", color = StarWhite.copy(alpha = 0.6f))
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无匹配应用", color = StarWhite.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    UninstallRow(app, accent) { pending = app }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("卸载应用") },
            text = {
                Text(
                    "确定卸载「${target.label}」吗？\n${target.packageName}" +
                        if (target.isSystem) "\n\n这是系统应用，卸载将回退到出厂版本。" else "",
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            TianjiInstaller.uninstall(context, target.packageName)
                        }
                        busy = false
                        pending = null
                        val detail = result.fold(
                            onSuccess = { it },
                            onFailure = { "失败：${it.message ?: "未知错误"}" },
                        )
                        message = "${target.label}：$detail"
                        TianjiLog.add(
                            context,
                            TianjiLogEntry(System.currentTimeMillis(), "卸载", target.packageName, target.label, result.isSuccess, detail),
                        )
                        refreshKey++
                    }
                }) { Text(if (busy) "卸载中…" else "确认卸载", color = Color(0xFFFF6B9D)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pending = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun UninstallRow(app: UninstallEntry, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = beidouShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(beidouShape(14.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(beidouShape(14.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                StarGlyph(color = accent, size = 22.dp)
            }
        }
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
                fontFamily = FontFamily.Monospace,
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

/** 列出可卸载应用 */
private fun listUninstallable(context: Context, includeSystem: Boolean): List<UninstallEntry> {
    val pm = context.packageManager
    val infos = try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
    } catch (_: Throwable) {
        return emptyList()
    }
    return infos.mapNotNull { ai ->
        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (isSystem && !includeSystem) return@mapNotNull null
        // 过滤自身，避免卸载正在运行的宿主
        if (ai.packageName == context.packageName) return@mapNotNull null

        val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
        val icon = runCatching { ai.loadIcon(pm).toBitmap(96, 96) }.getOrNull()
        val version = runCatching { pm.getPackageInfo(ai.packageName, 0).versionName }.getOrNull()
        UninstallEntry(ai.packageName, label, version, isSystem, icon)
    }.sortedBy { it.label.lowercase() }
}
