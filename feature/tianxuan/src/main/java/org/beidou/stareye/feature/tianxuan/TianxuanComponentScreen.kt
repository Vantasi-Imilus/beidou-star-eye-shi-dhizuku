package org.beidou.stareye.feature.tianxuan

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

/**
 * 天璇中枢 —— 组件管理页。
 *
 * 展示应用内的 Activity / Service / Receiver / Provider 及启用状态，
 * 经天枢 shell（`pm enable / disable 包名/组件`）启停单个组件。
 */
@Composable
fun TianxuanComponentScreen(app: TianxuanApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianxuanFeature.star.order)

    var components by remember { mutableStateOf<List<TianxuanComponent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<ComponentKind?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var confirmTarget by remember { mutableStateOf<TianxuanComponent?>(null) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(app.packageName, refreshKey) {
        loading = true
        components = withContext(Dispatchers.IO) { TianxuanManager.components(context, app.packageName) }
        loading = false
    }

    val visible = remember(components, filter) {
        if (filter == null) components else components.filter { it.kind == filter }
    }

    fun applyChange(component: TianxuanComponent, enabled: Boolean) {
        scope.launch {
            busy = component.className
            message = ""
            val result = withContext(Dispatchers.IO) {
                TianxuanManager.setComponentEnabled(app.packageName, component.className, enabled)
            }
            busy = null
            message = result.fold(
                onSuccess = { "已${if (enabled) "启用" else "停用"}：${component.shortName}" },
                onFailure = { "失败：${it.message ?: "未知错误"}" },
            )
            TianxuanLog.add(
                context,
                TianxuanLogEntry(
                    System.currentTimeMillis(),
                    if (enabled) "启用组件" else "停用组件",
                    app.packageName,
                    "${app.label} · ${component.shortName}",
                    result.isSuccess,
                    message,
                ),
            )
            refreshKey++
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = beidouShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("组件管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    "${app.label} · ${app.packageName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(beidouShape(50))
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
        }

        Spacer(Modifier.height(12.dp))

        // 过滤器
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip("全部", filter == null, accent) { filter = null }
            ComponentKind.entries.forEach { kind ->
                KindChip(
                    "${kind.label} ${components.count { it.kind == kind }}",
                    filter == kind,
                    accent,
                ) { filter = kind }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!TianxuanManager.shizukuReady()) {
            Box(
                modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(16.dp)).padding(12.dp),
            ) {
                Text(
                    "✦ 启停组件需要天枢中枢（Shizuku）就绪；当前只读展示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StarGold,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在读取组件…", color = StarWhite.copy(alpha = 0.6f))
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无组件", color = StarWhite.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.className }) { component ->
                    ComponentRow(
                        component = component,
                        accent = accent,
                        busy = busy == component.className,
                        onToggle = { checked ->
                            // 启用可直接执行；停用需二次确认
                            if (checked) applyChange(component, true) else confirmTarget = component
                        },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    // 停用确认：组件停用可能影响应用运行，启用则无需确认
    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("停用组件") },
            text = {
                Text("停用「${target.shortName}」后，应用的相关功能可能无法使用，确定继续吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    applyChange(target, false)
                }) { Text("停用", color = Color(0xFFFF6B9D)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(beidouShape(50))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else StarWhite.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ComponentRow(
    component: TianxuanComponent,
    accent: Color,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    component.shortName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (component.enabled) StarWhite else StarWhite.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    component.kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.8f),
                )
            }
            Text(
                component.className,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (component.exported) {
                    ComponentBadge("导出", StarGold)
                }
                component.permission?.let { ComponentBadge(it.substringAfterLast('.'), StarWhite.copy(alpha = 0.55f)) }
            }
        }
        Switch(
            checked = component.enabled,
            enabled = !busy,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.3f),
                uncheckedThumbColor = StarWhite.copy(alpha = 0.5f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
            ),
        )
    }
}

@Composable
private fun ComponentBadge(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(beidouShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
