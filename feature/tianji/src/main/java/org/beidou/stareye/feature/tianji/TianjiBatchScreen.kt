package org.beidou.stareye.feature.tianji

import android.net.Uri
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 天玑中枢 —— 批量安装队列页面。
 *
 * 两阶段：
 *  ① 入队解析：逐个「复制到缓存 → 解析 → 黑名单初筛」，进度实时刷新；
 *  ② 执行：交给 [TianjiBatchExecutor] 串行安装，本页只负责订阅状态与渲染。
 *
 * 由安装标签页（多选文件）与转接层（`ACTION_SEND_MULTIPLE`）共同复用。
 */
@Composable
fun TianjiBatchScreen(uris: List<Uri>, onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(TianjiFeature.star.order)
    val scope = rememberCoroutineScope()

    var executor by remember(uris) { mutableStateOf<TianjiBatchExecutor?>(null) }
    var parsingDone by remember(uris) { mutableStateOf(0) }
    val parsingTotal = uris.size

    // 入队解析阶段（IO 线程）；解析失败与黑名单命中在此标记为失败/拦截
    LaunchedEffect(uris) {
        val items = withContext(Dispatchers.IO) {
            uris.mapIndexed { i, uri ->
                val item = prepareBatchItem(context, i, uri)
                parsingDone = i + 1
                item
            }
        }
        executor = TianjiBatchExecutor(context, items, scope)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // 顶栏：返回 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "批量安装（${uris.size} 项）",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StarWhite,
                )
                Text(
                    "串行执行 · 黑名单同样生效 · 每次安装均写入记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val current = executor
        if (current == null) {
            // 入队解析中
            Column(
                modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("正在解析安装包…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                LinearProgressIndicator(
                    progress = { if (parsingTotal > 0) parsingDone.toFloat() / parsingTotal else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
                Text(
                    "$parsingDone / $parsingTotal",
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                )
            }
        } else {
            val state by current.state.collectAsState()
            BatchPanel(state = state, accent = accent, onStart = current::start, onAbort = current::abort)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 执行阶段面板：总进度 + 开始/中止 + 完成汇总 + 队列列表。
 */
@Composable
private fun BatchPanel(
    state: TianjiBatchState,
    accent: Color,
    onStart: () -> Unit,
    onAbort: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 总进度
        Column(
            modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦ 总进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.doneCount} / ${state.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            val progress = if (state.items.isEmpty()) 0f else state.doneCount.toFloat() / state.items.size
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
            if (state.stage.isNotEmpty()) {
                Text(
                    state.stage,
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 开始 / 中止
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BatchButton(
                label = "开始批量安装",
                color = accent,
                enabled = !state.running && !state.finished,
                onClick = onStart,
                modifier = Modifier.weight(1f),
            )
            BatchButton(
                label = "中止",
                color = Color(0xFFFF6B9D),
                enabled = state.running,
                onClick = onAbort,
                modifier = Modifier.weight(1f),
            )
        }

        // 完成汇总（成功 / 失败 + 失败原因列表）
        if (state.finished) {
            SummaryCard(state = state, accent = accent)
        }

        // 队列列表
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.items.forEach { item ->
                BatchItemRow(item = item, accent = accent)
            }
        }
    }
}

/**
 * 完成后的汇总卡片：成功/失败计数、中止提示与失败原因明细。
 */
@Composable
private fun SummaryCard(state: TianjiBatchState, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("✦ 批量完成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        Row(Modifier.fillMaxWidth()) {
            Text(
                "成功 ${state.successCount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = StarBlue,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "失败 ${state.failureCount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF6B9D),
            )
        }
        if (state.aborted && state.pendingCount > 0) {
            Text(
                "已中止：剩余 ${state.pendingCount} 项未安装",
                style = MaterialTheme.typography.labelMedium,
                color = StarGold,
            )
        }
        val failed = state.items.filter {
            it.status == TianjiBatchStatus.FAILED || it.status == TianjiBatchStatus.BLOCKED
        }
        if (failed.isNotEmpty()) {
            Text(
                "失败明细",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = StarWhite.copy(alpha = 0.6f),
            )
            failed.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(10.dp),
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = StarWhite.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.error ?: "未知错误",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF6B9D),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 队列条目行：图标 / 应用名 / 包名 / 版本 / 大小 / 状态。
 */
@Composable
private fun BatchItemRow(item: TianjiBatchItem, accent: Color) {
    val (statusText, statusColor) = when (item.status) {
        TianjiBatchStatus.PENDING -> "待安装" to StarWhite.copy(alpha = 0.5f)
        TianjiBatchStatus.INSTALLING -> "安装中…" to accent
        TianjiBatchStatus.SUCCESS -> "成功" to StarBlue
        TianjiBatchStatus.FAILED -> "失败" to Color(0xFFFF6B9D)
        TianjiBatchStatus.BLOCKED -> "已拦截" to Color(0xFFFF6B9D)
    }

    Row(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.apk?.icon != null) {
            Image(
                bitmap = item.apk.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                StarGlyph(color = accent, size = 20.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = StarWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildString {
                if (item.packageName.isNotEmpty()) {
                    append(item.packageName)
                    append(" · ")
                }
                val apk = item.apk
                if (apk != null) {
                    append("版本 ${apk.versionName ?: "—"} (${apk.versionCode}) · ${formatBytes(item.sizeBytes)}")
                } else {
                    append(item.fileName)
                }
            }
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
        )
    }
}

/**
 * 批量页操作按钮（胶囊底）。
 */
@Composable
private fun BatchButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (enabled) 0.18f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) color else StarWhite.copy(alpha = 0.3f),
        )
    }
}
