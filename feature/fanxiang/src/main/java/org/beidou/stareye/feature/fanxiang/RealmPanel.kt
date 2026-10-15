package org.beidou.stareye.feature.fanxiang

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.DeepSpaceLight
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.beidouShape
import org.beidou.stareye.ui.theme.starAccent

/** 星界内的页面（七中枢映射） */
enum class RealmPage { MAIN, CORE, APPS, INSTALL, TERMINAL, FREEZE, RESTRICT, CHANNEL }

/**
 * 反向星界面板 —— 反向模式下首页的主体。
 *
 * 星界（镜像空间）的生命周期与**七中枢的星界映射**：
 * 星界中枢（原开阳）/ 天璇·应用 / 天玑·映照 / 天权·终端 / 玉衡·限制 / 瑶光·冻结 / 天枢·通道。
 * 反色与锐利边框由主题层统一提供，本面板无需感知。
 */
@Composable
fun ReverseRealmPanel(modifier: Modifier = Modifier) {
    var page by remember { mutableStateOf(RealmPage.MAIN) }
    when (page) {
        RealmPage.MAIN -> RealmMainPage(modifier) { page = it }
        RealmPage.CORE -> RealmCorePage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
        RealmPage.APPS -> RealmAppsPage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
        RealmPage.INSTALL -> RealmInstallPage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
        RealmPage.TERMINAL -> RealmTerminalPage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
        RealmPage.FREEZE -> RealmFreezePage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
        RealmPage.RESTRICT -> RealmRestrictPage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
        RealmPage.CHANNEL -> RealmChannelPage(onBack = { page = RealmPage.MAIN }, modifier = modifier)
    }
}

// ═════════════ 主页 ═════════════

@Composable
private fun RealmMainPage(modifier: Modifier, onOpen: (RealmPage) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(7)

    var ownerReady by remember { mutableStateOf(false) }
    var realmExists by remember { mutableStateOf(false) }
    var appCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        ownerReady = RealmState.ownerReady(context)
        realmExists = RealmState.exists(context)
        appCount = if (realmExists) {
            withContext(Dispatchers.IO) { RealmApps.list(context).size }
        } else {
            0
        }
    }

    fun run(label: String, block: () -> Result<*>) {
        scope.launch {
            busy = label
            message = ""
            val result = withContext(Dispatchers.IO) { block() }
            busy = null
            message = result.fold(
                onSuccess = { detail -> (detail as? String)?.takeIf { it.isNotBlank() } ?: "$label · 完成" },
                onFailure = { "$label · ${it.message ?: "失败"}" },
            )
            refreshKey++
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 星界状态卡 ──
        item {
            Column(
                modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarGlyph(color = accent, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "反向星界 · 镜像空间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StarWhite,
                    )
                }
                Text(
                    RealmState.describe(context) + if (realmExists) " · 应用 $appCount 个" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWhite.copy(alpha = 0.75f),
                )
                Text(
                    "镜像空间是系统级隔离用户：其中的应用与数据独立于主界，" +
                        "仍由映射自七中枢的星界能力统一管理。",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.55f),
                )
            }
        }

        // ── 开辟 / 快捷操作 ──
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!realmExists) {
                    RealmMainAction(
                        label = if (ownerReady) "开辟反向星界" else "需开阳中枢",
                        color = if (ownerReady) accent else Color.Gray,
                        enabled = ownerReady && busy == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        run("开辟星界") { RealmState.create(context) }
                    }
                } else {
                    RealmMainAction("映照应用", accent, busy == null, Modifier.weight(1f)) {
                        onOpen(RealmPage.INSTALL)
                    }
                    RealmMainAction("星界中枢", starAccent(6), busy == null, Modifier.weight(1f)) {
                        onOpen(RealmPage.CORE)
                    }
                }
            }
        }

        if (busy != null || message.isNotEmpty()) {
            item {
                Text(
                    message.ifEmpty { "正在$busy…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWhite.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── 七中枢的星界映射 ──
        item {
            Text(
                "✦ 星界中枢（七中枢映射）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = StarGold,
            )
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(RealmPortals, key = { it.page.name }) { portal ->
                    RealmPortalCard(portal = portal, enabled = realmExists) { onOpen(portal.page) }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** 星界映射的中枢入口定义 */
internal data class RealmPortal(
    val page: RealmPage,
    val title: String,
    val origin: String,
    val detail: String,
    val order: Int,
)

internal val RealmPortals = listOf(
    RealmPortal(RealmPage.CORE, "星界中枢", "开阳中枢", "星界生命周期 · 管理权", 6),
    RealmPortal(RealmPage.APPS, "逆·天璇中枢", "天璇中枢", "星界应用管理 · 冻结/强停", 2),
    RealmPortal(RealmPage.INSTALL, "逆·天玑中枢", "天玑中枢", "把主界应用复制进星界", 3),
    RealmPortal(RealmPage.TERMINAL, "逆·天权中枢", "天权中枢", "shell 通道 · 作用于星界", 4),
    RealmPortal(RealmPage.RESTRICT, "逆·玉衡中枢", "玉衡中枢", "空间级系统限制", 5),
    RealmPortal(RealmPage.FREEZE, "逆·瑶光中枢", "瑶光中枢", "批量冻结 / 解冻", 7),
    RealmPortal(RealmPage.CHANNEL, "逆·天枢中枢", "天枢中枢", "Shizuku 通道状态", 1),
)

@Composable
private fun RealmPortalCard(portal: RealmPortal, enabled: Boolean, onClick: () -> Unit) {
    val accent = starAccent(portal.order)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = beidouShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarGlyph(color = if (enabled) accent else Color.Gray, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                portal.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) StarWhite else StarWhite.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "映射自 ${portal.origin}",
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.8f),
        )
        Text(
            portal.detail,
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RealmMainAction(
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(beidouShape(16.dp))
            .background(color.copy(alpha = if (enabled) 0.16f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) color else StarWhite.copy(alpha = 0.35f),
        )
    }
}

/** 星界应用行（供各子页面复用） */
@Composable
internal fun RealmAppRow(app: RealmApp, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = beidouShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RealmAppIcon(app, accent, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyMedium,
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
        if (app.frozen) {
            Text("❄", style = MaterialTheme.typography.labelMedium, color = StarGold)
        }
    }
}

@Composable
internal fun RealmAppIcon(app: RealmApp, accent: Color, size: androidx.compose.ui.unit.Dp) {
    if (app.icon != null) {
        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(beidouShape(size / 3)),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(beidouShape(size / 3)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            StarGlyph(color = accent, size = size / 2)
        }
    }
}
