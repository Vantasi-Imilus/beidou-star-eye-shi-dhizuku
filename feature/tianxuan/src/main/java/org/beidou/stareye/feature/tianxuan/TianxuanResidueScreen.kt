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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 天璇中枢 —— 残留目录扫描页。
 *
 * 扫描 /sdcard/Android/{data,obb,media} 下「包已卸载」的遗留目录，
 * 逐项后台计算大小，删除需二次确认，全部操作写入天璇操作记录。
 */
@Composable
fun TianxuanResidueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianxuanFeature.star.order)

    var items by remember { mutableStateOf<List<TianxuanResidue>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var sizes by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmTarget by remember { mutableStateOf<TianxuanResidue?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val shizukuReady = remember { TianxuanManager.shizukuReady() }

    LaunchedEffect(refreshKey) {
        loading = true
        message = ""
        val report = withContext(Dispatchers.IO) { TianxuanManager.scanResidues(context) }
        items = report.items
        warnings = report.warnings
        loading = false
        // 后台逐项计算大小（限流 4 并发，避免同时拉起大量 shell 进程）
        sizes = emptyMap()
        val semaphore = Semaphore(4)
        report.items.forEach { r ->
            launch {
                semaphore.withPermit {
                    val size = withContext(Dispatchers.IO) { TianxuanManager.residueSizeBytes(r.path) }
                    sizes = sizes + (r.path to size)
                }
            }
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
                Text("残留扫描", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    "卸载应用遗留的外部目录 · /sdcard/Android/{data,obb,media}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(enabled = !loading && !busy) { refreshKey++ }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "重新扫描",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (loading || busy) StarWhite.copy(alpha = 0.4f) else accent,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 能力状态提示
        if (!shizukuReady) {
            Box(
                modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(16.dp)).padding(14.dp),
            ) {
                Text(
                    "✦ 残留扫描需要天枢中枢（Shizuku）就绪；当前不可用，请先激活并授权天枢。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StarGold,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // 部分目录扫描失败提示
        warnings.forEach { w ->
            Text(
                "✦ $w",
                style = MaterialTheme.typography.labelSmall,
                color = StarGold.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
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
                Text("正在扫描残留目录…", color = StarWhite.copy(alpha = 0.6f))
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (shizukuReady) "未发现残留目录，很干净 ✦" else "无法扫描残留目录",
                    color = StarWhite.copy(alpha = 0.5f),
                )
            }
        } else {
            Text(
                "发现 ${items.size} 处残留（大小逐个后台计算中）",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.path }) { residue ->
                    ResidueRow(
                        residue = residue,
                        size = sizes[residue.path],
                        computing = !sizes.containsKey(residue.path),
                        accent = accent,
                        busy = busy,
                        onDelete = { confirmTarget = residue },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    // 删除二次确认
    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("删除残留目录") },
            text = {
                Text("确定删除「${target.path}」吗？此操作不可恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    scope.launch {
                        busy = true
                        message = ""
                        val result = withContext(Dispatchers.IO) { TianxuanManager.deleteResidue(target.path) }
                        busy = false
                        val ok = result.isSuccess
                        val detail = if (ok) "已删除：${target.path}" else "失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                        message = detail
                        TianxuanLog.add(
                            context,
                            TianxuanLogEntry(System.currentTimeMillis(), "删除残留", target.packageName, target.path, ok, detail),
                        )
                        if (ok) items = items.filter { it.path != target.path }
                    }
                }) { Text("删除", color = Color(0xFFFF6B9D)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("取消") }
            },
        )
    }
}

// ───────────────────────── 条目行 ─────────────────────────

@Composable
private fun ResidueRow(
    residue: TianxuanResidue,
    size: Long?,
    computing: Boolean,
    accent: Color,
    busy: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    residue.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StarWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                CategoryBadge(residue.category, accent)
            }
            Text(
                residue.path,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    computing -> "计算大小中…"
                    size != null -> formatBytes(size)
                    else -> "大小未知（可能无权限或目录已消失）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (size != null) accent.copy(alpha = 0.85f) else StarWhite.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                .clickable(enabled = !busy) { onDelete() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                "删除",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (busy) StarWhite.copy(alpha = 0.35f) else Color(0xFFFF6B9D),
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: String, accent: Color) {
    val color = when (category) {
        "data" -> StarBlue
        "obb" -> StarGold
        else -> accent
    }
    Text(
        category,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
