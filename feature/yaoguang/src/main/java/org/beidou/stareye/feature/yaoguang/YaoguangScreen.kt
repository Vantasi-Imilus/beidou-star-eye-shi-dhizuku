package org.beidou.stareye.feature.yaoguang

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.components.rememberFreezeRevision
import org.beidou.stareye.ui.theme.DeepSpaceLight
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

private val TABS = listOf("首页", "应用", "设置")

/**
 * 瑶光中枢 —— 雹（Hail）应用冻结 / 隐藏界面。
 *
 * 三页结构：首页（我的清单，按标签分页）· 应用（全部应用）· 设置。
 * 冻结状态读自 core:freeze 统一判定源，并订阅 [rememberFreezeRevision]，
 * 与天璇 / 玉衡保持跨中枢一致。
 */
@Composable
fun YaoguangScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(YaoguangFeature.star.order)
    val scope = rememberCoroutineScope()
    val freezeRevision = rememberFreezeRevision()

    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    var apps by remember { mutableStateOf<List<YaoguangApp>>(emptyList()) }
    var tags by remember { mutableStateOf<List<YaoguangTag>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            apps = YaoguangOps.loadApps(context)
            tags = YaoguangStore.tags(context)
        }
        loaded = true
    }

    LaunchedEffect(tick, freezeRevision) { reload() }

    LaunchedEffect(Unit) {
        // 让雹兼容别名组件的启用状态与设置保持一致
        YaoguangStore.syncHailCompat(context)
        // 自动冻结开启时确保常驻服务在运行（应用被重装 / 回收后恢复）
        if (YaoguangStore.autoFreeze(context)) {
            YaoguangAutoFreezeService.setEnabled(context, true)
        }
    }

    fun report(text: String) {
        message = text
        tick++
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.glass(shape = beidouShape(50)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        YaoguangFeature.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StarWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        YaoguangCore.describe(
                            context, YaoguangStore.channel(context), YaoguangStore.kind(context),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                    )
                }
                StarGlyph(color = accent, size = 22.dp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TABS.forEachIndexed { index, label ->
                    YaoguangChip(label, tab == index, accent) {
                        tab = index
                        message = ""
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (message.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(beidouShape(12.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = StarWhite.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = StarWhite.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { message = "" },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    0 -> HomeTab(context, accent, apps.filter { it.checked }, tags, ::report)
                    1 -> AppsTab(context, accent, apps, tags, ::report)
                    else -> YaoguangSettingsTab(context, accent, ::report)
                }
                if (!loaded) {
                    Text(
                        "正在读取应用列表…",
                        style = MaterialTheme.typography.labelMedium,
                        color = StarWhite.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

// ── 首页：我的清单 ──

@Composable
private fun HomeTab(
    context: Context,
    accent: Color,
    checkedApps: List<YaoguangApp>,
    tags: List<YaoguangTag>,
    onReport: (String) -> Unit,
) {
    var selectedTag by remember { mutableIntStateOf(DEFAULT_TAG) }
    var query by remember { mutableStateOf("") }
    var multiSelect by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionApp by remember { mutableStateOf<YaoguangApp?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fuzzy = remember { YaoguangStore.fuzzySearch(context) }
    val nineKey = remember { YaoguangStore.nineKeySearch(context) }

    val visible = remember(checkedApps, selectedTag, query, fuzzy, nineKey) {
        checkedApps
            .filter { selectedTag in it.tagIds }
            .filter { YaoguangOps.matches(it, query, fuzzy, nineKey) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                val count = checkedApps.count { tag.id in it.tagIds }
                YaoguangChip("${tag.name} $count", tag.id == selectedTag, accent) {
                    selectedTag = tag.id
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        YaoguangSearchField(query, "搜索清单（名称 / 拼音 / 九键）", accent) { query = it }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YaoguangChip(if (multiSelect) "退出多选" else "多选", multiSelect, accent) {
                multiSelect = !multiSelect
                selected = emptySet()
            }
            if (multiSelect) {
                YaoguangChip("全选", false, accent) { selected = visible.map { it.packageName }.toSet() }
                YaoguangChip("冻结(${selected.size})", selected.isNotEmpty(), accent) {
                    val targets = visible.filter { it.packageName in selected }.map { it.packageName }
                    onReport(YaoguangOps.setFrozenBatch(context, targets, true).text)
                }
                YaoguangChip("解冻(${selected.size})", selected.isNotEmpty(), accent) {
                    val targets = visible.filter { it.packageName in selected }.map { it.packageName }
                    onReport(YaoguangOps.setFrozenBatch(context, targets, false).text)
                }
                YaoguangChip("打标签", selected.isNotEmpty(), accent) { showTagDialog = true }
                YaoguangChip("移出清单", selected.isNotEmpty(), accent) {
                    YaoguangStore.remove(context, selected)
                    onReport("已移出 ${selected.size} 个应用")
                    selected = emptySet()
                }
            } else {
                YaoguangChip("冻结本页", visible.isNotEmpty(), accent) {
                    onReport(YaoguangOps.setFrozenBatch(context, visible.map { it.packageName }, true).text)
                }
                YaoguangChip("冻结全部", true, accent) { onReport(YaoguangOps.freezeAll(context).text) }
                YaoguangChip("解冻全部", true, accent) { onReport(YaoguangOps.unfreezeAll(context).text) }
                YaoguangChip("冻结非白名单", true, accent) {
                    onReport(YaoguangOps.freezeNonWhitelisted(context).text)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Text(
                if (checkedApps.isEmpty()) "清单还是空的 —— 到「应用」页点击应用即可加入" else "没有匹配的应用",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    AppGridItem(
                        app = app,
                        accent = accent,
                        selected = app.packageName in selected,
                        multiSelect = multiSelect,
                        onClick = {
                            if (multiSelect) {
                                selected = if (app.packageName in selected) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            } else {
                                scope.launch {
                                    onReport(
                                        YaoguangOps.launch(context, app.packageName)
                                            .fold({ "已启动 ${app.label}" }, { it.message ?: "启动失败" }),
                                    )
                                }
                            }
                        },
                        onLongClick = { actionApp = app },
                    )
                }
            }
        }
    }

    actionApp?.let { app ->
        AppActionDialog(
            context = context,
            app = app,
            tags = tags,
            accent = accent,
            onDismiss = { actionApp = null },
            onChanged = onReport,
        )
    }

    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("附加标签") },
            text = {
                Column {
                    tags.forEach { tag ->
                        Text(
                            tag.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(beidouShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .clickable {
                                    YaoguangStore.setTag(context, selected, tag.id, attached = true)
                                    showTagDialog = false
                                    onReport("已为 ${selected.size} 个应用附加「${tag.name}」")
                                },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTagDialog = false }) { Text("关闭") } },
            containerColor = DeepSpaceLight,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    app: YaoguangApp,
    accent: Color,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(beidouShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        YaoguangAppIcon(app, 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (app.frozen) StarWhite.copy(alpha = 0.55f) else StarWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (multiSelect && selected) {
            Text("已选", style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

// ── 应用页：全部应用 ──

@Composable
private fun AppsTab(
    context: Context,
    accent: Color,
    apps: List<YaoguangApp>,
    tags: List<YaoguangTag>,
    onReport: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var userOnly by remember { mutableStateOf(true) }
    var systemToo by remember { mutableStateOf(false) }
    var frozenOnly by remember { mutableStateOf(false) }
    var unfrozenOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableIntStateOf(0) }   // 0 名称 1 安装时间 2 更新时间
    var actionApp by remember { mutableStateOf<YaoguangApp?>(null) }

    val fuzzy = remember { YaoguangStore.fuzzySearch(context) }
    val nineKey = remember { YaoguangStore.nineKeySearch(context) }

    val visible = remember(apps, query, userOnly, systemToo, frozenOnly, unfrozenOnly, sort, fuzzy, nineKey) {
        apps.asSequence()
            .filter { app ->
                val typeOk = (userOnly && !app.isSystem) || (systemToo && app.isSystem)
                val stateOk = when {
                    frozenOnly && unfrozenOnly -> true
                    frozenOnly -> app.frozen
                    unfrozenOnly -> !app.frozen
                    else -> true
                }
                typeOk && stateOk
            }
            .filter { YaoguangOps.matches(it, query, fuzzy, nineKey) }
            .let { seq ->
                when (sort) {
                    1 -> seq.sortedByDescending { it.installTime }
                    2 -> seq.sortedByDescending { it.updateTime }
                    else -> seq
                }
            }
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        YaoguangSearchField(query, "搜索全部应用", accent) { query = it }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            YaoguangChip("用户应用", userOnly, accent) { userOnly = !userOnly }
            YaoguangChip("系统应用", systemToo, accent) { systemToo = !systemToo }
            YaoguangChip("已冻结", frozenOnly, accent) { frozenOnly = !frozenOnly }
            YaoguangChip("未冻结", unfrozenOnly, accent) { unfrozenOnly = !unfrozenOnly }
            YaoguangChip(
                when (sort) {
                    1 -> "按安装时间"
                    2 -> "按更新时间"
                    else -> "按名称"
                },
                false,
                accent,
            ) { sort = (sort + 1) % 3 }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(visible, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    accent = accent,
                    onClick = {
                        if (app.checked) {
                            YaoguangStore.remove(context, listOf(app.packageName))
                            onReport("已移出清单：${app.label}")
                        } else {
                            YaoguangStore.add(context, app.packageName)
                            onReport("已加入清单：${app.label}")
                        }
                    },
                    onLongClick = { actionApp = app },
                )
            }
        }
    }

    actionApp?.let { app ->
        AppActionDialog(
            context = context,
            app = app,
            tags = tags,
            accent = accent,
            onDismiss = { actionApp = null },
            onChanged = onReport,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: YaoguangApp,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(beidouShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YaoguangAppIcon(app, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (app.frozen) StarWhite.copy(alpha = 0.6f) else StarWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Text("置顶", style = MaterialTheme.typography.labelSmall, color = accent)
                }
                if (app.whitelisted) {
                    Spacer(Modifier.width(6.dp))
                    Text("白名单", style = MaterialTheme.typography.labelSmall, color = StarGold)
                }
            }
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        YaoguangStateBadge(app, accent)
        Spacer(Modifier.width(8.dp))
        Text(
            if (app.checked) "★" else "☆",
            style = MaterialTheme.typography.titleMedium,
            color = if (app.checked) accent else StarWhite.copy(alpha = 0.35f),
        )
    }
}

// ── 长按动作菜单 ──

@Composable
private fun AppActionDialog(
    context: Context,
    app: YaoguangApp,
    tags: List<YaoguangTag>,
    accent: Color,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tagPickerOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }

    fun run(block: () -> String) {
        onChanged(block())
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                ActionText("启动${if (app.frozen) "（自动解冻）" else ""}") {
                    scope.launch {
                        onDismiss()
                        onChanged(
                            YaoguangOps.launch(context, app.packageName)
                                .fold({ "已启动 ${app.label}" }, { it.message ?: "启动失败" }),
                        )
                    }
                }
                ActionText(if (app.frozen) "解冻" else "冻结") {
                    run {
                        YaoguangOps.setFrozen(context, app.packageName, !app.frozen)
                            .fold({ if (app.frozen) "已解冻" else "已冻结" }, { it.message ?: "失败" })
                    }
                }
                if (!app.frozen) {
                    // 注意：不可在此处 onDismiss() —— 宿主对话框被移除会连带销毁本组件的
                    // scheduleOpen 状态，导致延时选择对话框永远不显示（真机发现的 bug）。
                    // 改为层叠显示，选择完成后再一并收起菜单。
                    ActionText("延时冻结…") {
                        scheduleOpen = true
                    }
                }
                ActionText(if (app.checked) "移出清单" else "加入清单") {
                    run {
                        if (app.checked) {
                            YaoguangStore.remove(context, listOf(app.packageName))
                            "已移出清单"
                        } else {
                            YaoguangStore.add(context, app.packageName)
                            "已加入清单"
                        }
                    }
                }
                ActionText(if (app.whitelisted) "取消白名单" else "加入白名单") {
                    run {
                        YaoguangStore.setWhitelisted(context, app.packageName, !app.whitelisted)
                        if (app.whitelisted) "已取消白名单" else "已加入白名单（冻结时跳过）"
                    }
                }
                ActionText(if (app.pinned) "取消置顶" else "置顶") {
                    run {
                        YaoguangStore.setPinned(context, app.packageName, !app.pinned)
                        if (app.pinned) "已取消置顶" else "已置顶"
                    }
                }
                ActionText("标签…") {
                    onDismiss()
                    tagPickerOpen = true
                }
                ActionText("复制包名") {
                    run {
                        copyToClipboard(context, app.packageName)
                        "已复制 ${app.packageName}"
                    }
                }
                if (app.installed) {
                    ActionText("卸载") {
                        run {
                            YaoguangOps.uninstall(context, app.packageName)
                                .fold({ "已卸载 ${app.label}" }, { it.message ?: "卸载失败" })
                        }
                    }
                } else {
                    ActionText("恢复安装") {
                        run {
                            YaoguangOps.reinstall(context, app.packageName)
                                .fold({ "已恢复安装 ${app.label}" }, { it.message ?: "恢复失败" })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        containerColor = DeepSpaceLight,
    )

    if (tagPickerOpen) {
        AlertDialog(
            onDismissRequest = { tagPickerOpen = false },
            title = { Text("标签 · ${app.label}") },
            text = {
                Column {
                    tags.forEach { tag ->
                        val attached = tag.id in app.tagIds
                        Text(
                            (if (attached) "✓ " else "") + tag.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (attached) accent else StarWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(beidouShape(10.dp))
                                .clickable {
                                    YaoguangStore.setTag(
                                        context, listOf(app.packageName), tag.id, attached = !attached,
                                    )
                                    onChanged(if (attached) "已移除标签「${tag.name}」" else "已附加标签「${tag.name}」")
                                    tagPickerOpen = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tagPickerOpen = false }) { Text("关闭") } },
            containerColor = DeepSpaceLight,
        )
    }

    if (scheduleOpen) {
        AlertDialog(
            onDismissRequest = { scheduleOpen = false },
            title = { Text("延时冻结 · ${app.label}") },
            text = {
                Column {
                    listOf(10, 15, 30, 45, 60).forEach { minutes ->
                        Text(
                            "$minutes 分钟后",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(beidouShape(10.dp))
                                .clickable {
                                    YaoguangOps.scheduleFreeze(context, app.packageName, minutes)
                                    onChanged("已排定：${app.label} · $minutes 分钟后冻结")
                                    scheduleOpen = false
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scheduleOpen = false }) { Text("关闭") } },
            containerColor = DeepSpaceLight,
        )
    }
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = StarWhite,
        modifier = Modifier
            .fillMaxWidth()
            .clip(beidouShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("package", text))
}
