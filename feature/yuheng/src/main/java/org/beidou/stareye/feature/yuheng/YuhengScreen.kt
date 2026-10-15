package org.beidou.stareye.feature.yuheng

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.beidou.stareye.core.freeze.FreezeState
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.components.rememberFreezeRevision
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

private val TABS = listOf("系统", "应用", "用户", "限制", "密码", "网络", "设置")

/**
 * 玉衡中枢 —— OwnDroid（设备所有者深度管控）界面。
 *
 * 七大分页覆盖 OwnDroid 全部功能域：系统 / 应用 / 用户 / 用户限制 / 密码与锁屏 / 网络 / 设置。
 */
@Composable
fun YuhengScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = starAccent(YuhengFeature.star.order)

    var tab by remember { mutableIntStateOf(0) }
    var subPage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val ownerReady = remember(tick) { YuhengCore.hasOwner(context) }
    val showDangerous = remember(tick) { YuhengPrefs.showDangerous(context) }

    fun report(r: YuhengCore.Result) {
        message = r.text()
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
                    onClick = { if (subPage != null) subPage = null else onBack() },
                    modifier = Modifier.glass(shape = beidouShape(50)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        subPage?.let { YuhengOps.entryTitle(it) } ?: YuhengFeature.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StarWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (ownerReady) YuhengCore.privilegeSummary(context) else "未激活设备所有者",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ownerReady) accent else StarGold,
                        maxLines = 1,
                    )
                }
                StarGlyph(color = accent, size = 22.dp)
            }

            Spacer(Modifier.height(12.dp))

            // 分页条
            if (subPage == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TABS.forEachIndexed { index, label ->
                        YuhengTabChip(label, tab == index, accent) { tab = index; message = "" }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (message.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(beidouShape(12.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .clickable { message = "" }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(message, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
                    Text("✕", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
                }
                Spacer(Modifier.height(8.dp))
            }

            // 内容
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val page = subPage
                when {
                    page != null -> YuhengSubPage(
                        id = page,
                        accent = accent,
                        onReport = ::report,
                    )
                    tab == 0 -> SystemTab(accent, ownerReady, showDangerous, tick, ::report) { subPage = it }
                    tab == 1 -> AppsTab(accent, tick, ::report)
                    tab == 2 -> UsersTab(accent, ownerReady, tick, ::report) { subPage = it }
                    tab == 3 -> RestrictionsTab(accent, ownerReady, tick, ::report)
                    tab == 4 -> PasswordTab(accent, ownerReady, showDangerous, tick, ::report) { subPage = it }
                    tab == 5 -> NetworkTab(accent, tick, ::report) { subPage = it }
                    else -> SettingsTab(accent, tick, ::report)
                }
            }
        }
    }
}

// ═════════════════════ 系统 ═════════════════════

@Composable
private fun SystemTab(
    accent: Color,
    ownerReady: Boolean,
    showDangerous: Boolean,
    tick: Int,
    report: (YuhengCore.Result) -> Unit,
    openScreen: (String) -> Unit,
) {
    val context = LocalContext.current
    val states = remember(tick) {
        YuhengSystemCatalog.all.associate { it.id to YuhengOps.systemRead(context, it.id) }
    }
    val enums = remember(tick) {
        YuhengSystemCatalog.all.filter { it.kind == EntryKind.ENUM }
            .associate { it.id to YuhengOps.enumIndex(context, it.id) }
    }
    val texts = remember(tick) {
        YuhengSystemCatalog.all.filter { it.kind == EntryKind.TEXT }
            .associate { it.id to YuhengOps.textValue(context, it.id) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSystemCatalog.groups.forEach { group ->
            val visible = group.entries.filter { it.availableOn() && (showDangerous || !it.danger) }
            if (visible.isEmpty()) return@forEach
            YuhengSection(group.title, accent) {
                visible.forEach { e ->
                    YuhengFeatureRenderer(
                        entry = e, accent = accent, ownerReady = ownerReady, showDangerous = showDangerous,
                        isChecked = { states[it] == true },
                        onToggle = { id, v -> report(YuhengOps.systemWrite(context, id, v)) },
                        onAction = { id -> report(YuhengOps.systemAction(context, id)) },
                        onScreen = openScreen,
                        enumIndex = { enums[it] ?: 0 },
                        enumOptions = { YuhengOps.enumOptions(it) },
                        onEnum = { id, idx -> report(YuhengOps.enumWrite(context, id, idx)) },
                        textValue = { texts[it] ?: "" },
                        onText = { id, v -> report(YuhengOps.textWrite(context, id, v)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ═════════════════════ 应用 ═════════════════════

/** 应用条目 */
private data class AppEntry(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val version: String,
    /** 统一冻结形态标签（挂起/隐藏/禁用）；正常为 null —— 判定源为 core:freeze */
    val freezeLabel: String?,
)

@Composable
private fun AppsTab(accent: Color, tick: Int, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var showSystem by remember { mutableStateOf(false) }
    // 订阅跨中枢冻结变更：天璇 / 瑶光改动后列表角标自动刷新
    val freezeRevision = rememberFreezeRevision()

    val apps = remember(tick, showSystem, freezeRevision) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { if (showSystem) true else (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { ai ->
                AppEntry(
                    pkg = ai.packageName,
                    label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
                    system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    version = runCatching { pm.getPackageInfo(ai.packageName, 0).versionName ?: "" }.getOrDefault(""),
                    freezeLabel = FreezeState.flagsOf(ai).kind
                        .takeIf { it != FreezeState.Kind.NONE }?.label,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    val current = selected
    if (current != null) {
        AppDetailPage(current, accent, tick, report) { selected = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${apps.size} 个应用", style = MaterialTheme.typography.labelMedium, color = StarWhite.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            YuhengTabChip(if (showSystem) "含系统" else "仅用户", showSystem, accent) { showSystem = !showSystem }
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)),
            placeholder = { Text("搜索应用名或包名…", color = StarWhite.copy(alpha = 0.35f)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = accent, focusedTextColor = StarWhite, unfocusedTextColor = StarWhite,
            ),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.pkg }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(16.dp))
                        .clickable { selected = app }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                            app.pkg,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = StarWhite.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (app.freezeLabel != null) {
                        Text(
                            "❄ ${app.freezeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StarGold,
                            modifier = Modifier
                                .clip(beidouShape(6.dp))
                                .background(StarGold.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (app.system) "系统" else "用户", style = MaterialTheme.typography.labelSmall, color = if (app.system) StarGold else accent)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AppDetailPage(
    app: AppEntry,
    accent: Color,
    tick: Int,
    report: (YuhengCore.Result) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ops = YuhengAppCatalog.batchOps
    // 订阅跨中枢冻结变更：天璇 / 瑶光改动后此处自动重读
    val freezeRevision = rememberFreezeRevision()
    val states = remember(tick, app.pkg, freezeRevision) {
        ops.associate { it.id to YuhengOps.appState(context, it.id, app.pkg) }
    }
    // 统一冻结形态（core:freeze 判定）：与天璇 / 瑶光显示口径一致
    val freezeLabel = remember(tick, app.pkg, freezeRevision) {
        FreezeState.flags(context, app.pkg).kind.takeIf { it != FreezeState.Kind.NONE }?.label
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection(app.label, accent) {
            YuhengInfoRow("包名", app.pkg)
            YuhengInfoRow("版本", app.version.ifBlank { "—" })
            YuhengInfoRow("类型", if (app.system) "系统应用" else "用户应用")
            YuhengInfoRow("冻结状态", freezeLabel ?: "正常")
        }

        YuhengSection("批量策略", accent) {
            ops.filter { it.availableOn() }.forEach { op ->
                YuhengToggleRow(op.title, op.detail, states[op.id] == true, accent) { v ->
                    report(YuhengOps.appToggle(context, op.id, app.pkg, v))
                }
            }
        }

        YuhengSection("操作", accent) {
            YuhengActionRow("清除应用数据", "删除该应用的全部数据与缓存", StarGold, enabled = Build.VERSION.SDK_INT >= 28) {
                report(YuhengOps.clearAppData(context, app.pkg))
            }
            YuhengActionRow("卸载", "交由系统卸载器处理", Color(0xFFFF6B9D)) {
                report(YuhengOps.uninstallApp(context, app.pkg))
            }
            YuhengActionRow("系统应用详情", "跳转系统设置中的应用信息页", StarBlue) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        YuhengSection("权限管理", accent) {
            Text(
                "点按可查看并修改该应用的运行时权限授予状态（需设备所有者）。",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
            AppPermissionList(app.pkg, accent, tick, report)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppPermissionList(pkg: String, accent: Color, tick: Int, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    val perms = remember(pkg) {
        runCatching {
            context.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }
    if (perms.isEmpty()) {
        Text("该应用未申请权限", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.45f))
        return
    }
    val states = remember(tick, pkg) { perms.associateWith { YuhengOps.permissionState(context, pkg, it) } }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        perms.forEach { perm ->
            val st = states[perm] ?: DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            val label = when (st) {
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> "已授予"
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> "已拒绝"
                else -> "默认"
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    perm.substringAfterLast('.'),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = StarWhite.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier
                        .clip(beidouShape(50))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable {
                            val next = when (st) {
                                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                                else -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                            }
                            report(YuhengOps.setPermissionState(context, pkg, perm, next))
                        }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ═════════════════════ 用户 ═════════════════════

@Composable
private fun UsersTab(
    accent: Color,
    ownerReady: Boolean,
    tick: Int,
    report: (YuhengCore.Result) -> Unit,
    openScreen: (String) -> Unit,
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    val users = remember(tick) { YuhengOps.secondaryUsers(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("当前用户", accent) {
            YuhengInfoRow("用户 ID", YuhengCore.currentUserId().toString())
            YuhengInfoRow("权限", YuhengCore.privilegeSummary(context))
            YuhengInfoRow("受管理工作资料", if (YuhengCore.isManagedProfile(context)) "是" else "否")
        }

        YuhengSection("用户管理", accent) {
            YuhengUserCatalog.actions.filter { it.availableOn() && !it.deviceOnly }.forEach { e ->
                YuhengFeatureRenderer(
                    entry = e, accent = accent, ownerReady = ownerReady,
                    showDangerous = true,
                    isChecked = { false }, onToggle = { _, _ -> },
                    onAction = { }, onScreen = openScreen,
                    enumIndex = { 0 }, enumOptions = { emptyList() }, onEnum = { _, _ -> },
                    textValue = { YuhengOps.textValue(context, it) },
                    onText = { id, v -> report(YuhengOps.textWrite(context, id, v)) },
                )
            }
            if (ownerReady) {
                YuhengActionRow("创建用户", "新建一个受管理的用户", accent) { showCreate = true }
            }
        }

        if (users.isNotEmpty()) {
            YuhengSection("受管理用户（${users.size}）", accent) {
                users.forEach { user ->
                    val id = YuhengOps.userIdOf(user)
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("用户 #$id", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = StarWhite)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("start" to "启动", "switch" to "切换", "stop" to "停止", "remove" to "删除").forEach { (op, label) ->
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (op == "remove") Color(0xFFFF6B9D) else accent,
                                    modifier = Modifier
                                        .clip(beidouShape(50))
                                        .background((if (op == "remove") Color(0xFFFF6B9D) else accent).copy(alpha = 0.14f))
                                        .clickable { report(YuhengOps.userOperation(context, op, user)) }
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        YuhengSection("工作资料", accent) {
            YuhengUserCatalog.workProfile.filter { it.availableOn() }.forEach { e ->
                YuhengFeatureRenderer(
                    entry = e, accent = accent, ownerReady = ownerReady,
                    showDangerous = YuhengPrefs.showDangerous(context),
                    isChecked = { false }, onToggle = { _, _ -> },
                    onAction = { id ->
                        if (id == "wp_create") {
                            runCatching {
                                context.startActivity(
                                    Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                                        .putExtra(
                                            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                                            YuhengCore.admin(context),
                                        )
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                                report(YuhengCore.Result.Ok("已唤起工作资料创建流程"))
                            }.onFailure { report(YuhengCore.Result.Fail(it.message ?: "无法创建")) }
                        } else {
                            report(YuhengOps.systemAction(context, id))
                        }
                    },
                    onScreen = openScreen,
                    enumIndex = { 0 }, enumOptions = { emptyList() }, onEnum = { _, _ -> },
                    textValue = { YuhengOps.textValue(context, it) },
                    onText = { id, v -> report(YuhengOps.textWrite(context, id, v)) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("创建用户") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    report(YuhengOps.createUser(context, newName.ifBlank { "用户" }, ephemeral = false, skipSetup = true))
                    showCreate = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
}

// ═════════════════════ 用户限制 ═════════════════════

@Composable
private fun RestrictionsTab(accent: Color, ownerReady: Boolean, tick: Int, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("全部") }

    val states = remember(tick) {
        YuhengRestrictions.all.associate { it.key to YuhengOps.restrictionEnabled(context, it.key) }
    }
    val groups = remember { listOf("全部") + YuhengRestrictions.groupNames }

    val visible = remember(query, group) {
        YuhengRestrictions.all.filter { item ->
            (group == "全部" || item.group == group) &&
                (query.isBlank() || item.title.contains(query, true) || item.key.contains(query, true)) &&
                item.minApi <= Build.VERSION.SDK_INT
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)),
            placeholder = { Text("搜索限制项（共 ${YuhengRestrictions.all.size} 项）…", color = StarWhite.copy(alpha = 0.35f)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = accent, focusedTextColor = StarWhite, unfocusedTextColor = StarWhite,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEach { g ->
                YuhengTabChip(g, group == g, accent) { group = g }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "已启用 ${states.count { it.value }} 项 · 生效范围：设备所有者=整机，资料所有者=本资料",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.key }) { item ->
                YuhengToggleRow(
                    item.title,
                    item.key,
                    states[item.key] == true,
                    accent,
                    enabled = ownerReady,
                ) { v -> report(YuhengOps.restrictionSet(context, item.key, v)) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ═════════════════════ 密码 ═════════════════════

@Composable
private fun PasswordTab(
    accent: Color,
    ownerReady: Boolean,
    showDangerous: Boolean,
    tick: Int,
    report: (YuhengCore.Result) -> Unit,
    openScreen: (String) -> Unit,
) {
    val context = LocalContext.current
    val info = remember(tick) { YuhengOps.passwordInfo(context) }
    val enums = remember(tick) {
        YuhengPasswordCatalog.groups.flatMap { it.entries }.filter { it.kind == EntryKind.ENUM }
            .associate { it.id to YuhengOps.enumIndex(context, it.id) }
    }
    val texts = remember(tick) {
        YuhengPasswordCatalog.groups.flatMap { it.entries }.filter { it.kind == EntryKind.TEXT }
            .associate { it.id to YuhengOps.textValue(context, it.id) }
    }
    val keyguardLabels = remember(tick) { YuhengOps.enumOptions("keyguard_features") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("密码状态", accent) {
            info.forEach { (k, v) -> YuhengInfoRow(k, v) }
        }

        YuhengPasswordCatalog.groups.forEach { group ->
            val visible = group.entries.filter { it.availableOn() && (showDangerous || !it.danger) }
            if (visible.isEmpty()) return@forEach
            YuhengSection(group.title, accent) {
                visible.forEach { e ->
                    when (e.id) {
                        "keyguard_features" -> YuhengKeyguardFeatures(accent, tick, report)
                        "reset_token" -> YuhengResetToken(accent, tick, report)
                        "reset_password" -> YuhengResetPassword(accent, report)
                        else -> YuhengFeatureRenderer(
                            entry = e, accent = accent, ownerReady = ownerReady, showDangerous = showDangerous,
                            isChecked = { false }, onToggle = { _, _ -> },
                            onAction = { }, onScreen = openScreen,
                            enumIndex = { enums[it] ?: 0 },
                            enumOptions = { YuhengOps.enumOptions(it) },
                            onEnum = { id, idx -> report(YuhengOps.enumWrite(context, id, idx)) },
                            textValue = { texts[it] ?: "" },
                            onText = { id, v -> report(YuhengOps.textWrite(context, id, v)) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** 锁屏特性位掩码编辑 */
@Composable
private fun YuhengKeyguardFeatures(accent: Color, tick: Int, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    val features = remember {
        listOf(
            DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL to "全部小部件" to 21,
            DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA to "安全相机" to 21,
            DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS to "隐藏敏感通知内容" to 21,
            DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS to "显示完整通知" to 21,
            DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS to "信任代理" to 21,
            DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT to "指纹解锁" to 21,
            DevicePolicyManager.KEYGUARD_DISABLE_FACE to "人脸解锁" to 28,
            DevicePolicyManager.KEYGUARD_DISABLE_IRIS to "虹膜解锁" to 28,
            DevicePolicyManager.KEYGUARD_DISABLE_SHORTCUTS_ALL to "锁屏快捷方式" to 34,
        ).map { (pair, api) -> Triple(pair.first, pair.second, api) }
    }
    var mask by remember(tick) {
        mutableIntStateOf(runCatching { YuhengCore.dpm(context).getKeyguardDisabledFeatures(YuhengCore.admin(context)) }.getOrDefault(0))
    }

    Column {
        features.forEach { (flag, label, api) ->
            if (api > Build.VERSION.SDK_INT) return@forEach
            val on = (mask and flag) != 0
            YuhengToggleRow(label, "", on, accent) { v ->
                mask = if (v) mask or flag else mask and flag.inv()
                report(YuhengCore.call("禁用锁屏特性") {
                    YuhengCore.dpm(context).setKeyguardDisabledFeatures(YuhengCore.admin(context), mask)
                })
            }
        }
    }
}

/** 重置密码令牌管理 */
@Composable
private fun YuhengResetToken(accent: Color, tick: Int, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    val active = remember(tick) {
        runCatching {
            Build.VERSION.SDK_INT >= 26 &&
                YuhengCore.dpm(context).isResetPasswordTokenActive(YuhengCore.admin(context))
        }.getOrDefault(false)
    }

    Column {
        YuhengInfoRow("令牌状态", if (active) "已激活" else "未激活")
        Spacer(Modifier.height(8.dp))
        TextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("令牌（≥32 字节）", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "设置令牌",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier.clip(beidouShape(50)).background(accent.copy(alpha = 0.14f))
                    .clickable {
                        val value = token.ifBlank { YuhengPrefs.generateKey() + YuhengPrefs.generateKey() }
                        report(YuhengOps.setResetToken(context, value))
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "清除令牌",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF6B9D),
                modifier = Modifier.clip(beidouShape(50)).background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                    .clickable { report(YuhengOps.setResetToken(context, null)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 用令牌重置设备密码 */
@Composable
private fun YuhengResetPassword(accent: Color, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var pwd by remember { mutableStateOf("") }
    Column {
        YuhengDangerRow("危险操作：将直接修改设备锁屏密码")
        Spacer(Modifier.height(8.dp))
        TextField(
            value = pwd,
            onValueChange = { pwd = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("新密码（≥4 位）", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "重置密码",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF6B9D),
            modifier = Modifier.clip(beidouShape(50)).background(Color(0xFFFF6B9D).copy(alpha = 0.16f))
                .clickable { report(YuhengOps.textWrite(context, "reset_password", pwd)) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ═════════════════════ 网络 ═════════════════════

@Composable
private fun NetworkTab(
    accent: Color,
    tick: Int,
    report: (YuhengCore.Result) -> Unit,
    openScreen: (String) -> Unit,
) {
    val context = LocalContext.current
    val ownerReady = remember(tick) { YuhengCore.hasOwner(context) }
    val wifiOn = remember(tick) { YuhengOps.wifiEnabled(context) }
    val wifiList = remember(tick) { YuhengOps.configuredWifi(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("Wi-Fi 状态", accent) {
            YuhengToggleRow("Wi-Fi 开关", "", wifiOn, accent) { v ->
                report(YuhengOps.setWifiEnabled(context, v))
            }
            YuhengInfoRow("MAC 地址", YuhengOps.wifiMac(context))
            YuhengInfoRow("已保存网络", "${wifiList.size} 个")
        }

        if (wifiList.isNotEmpty()) {
            YuhengSection("已保存网络", accent) {
                wifiList.take(20).forEach { (id, ssid, _) ->
                    YuhengInfoRow(ssid, "networkId=$id")
                }
            }
        }

        YuhengNetworkCatalog.groups.forEach { group ->
            val visible = group.entries.filter { it.availableOn() }
            if (visible.isEmpty()) return@forEach
            YuhengSection(group.title, accent) {
                visible.forEach { e ->
                    YuhengFeatureRenderer(
                        entry = e, accent = accent, ownerReady = ownerReady,
                        showDangerous = true,
                        isChecked = { YuhengOps.enumIndex(context, it) != 0 },
                        onToggle = { id, v -> report(YuhengOps.systemWrite(context, id, v)) },
                        onAction = { },
                        onScreen = openScreen,
                        enumIndex = { YuhengOps.enumIndex(context, it) },
                        enumOptions = { YuhengOps.enumOptions(it) },
                        onEnum = { id, idx -> report(YuhengOps.enumWrite(context, id, idx)) },
                        textValue = { "" },
                        onText = { _, _ -> },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ═════════════════════ 设置 ═════════════════════

@Composable
private fun SettingsTab(accent: Color, tick: Int, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var dangerous by remember(tick) { mutableStateOf(YuhengPrefs.showDangerous(context)) }
    var apiOn by remember(tick) { mutableStateOf(YuhengPrefs.apiEnabled(context)) }
    var apiKey by remember(tick) { mutableStateOf(YuhengPrefs.apiKey(context)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("通用", accent) {
            YuhengToggleRow("显示危险功能", "显示擦除数据、重启、ADB 调试等高危项", dangerous, StarGold) { v ->
                dangerous = v
                YuhengPrefs.setShowDangerous(context, v)
            }
        }

        YuhengSection("对外 API", accent) {
            YuhengToggleRow("启用 API", "允许外部应用通过广播调用玉衡功能", apiOn, accent) { v ->
                if (v && apiKey.length < 8) {
                    report(YuhengCore.Result.Fail("密钥长度需 ≥ 8 位"))
                } else {
                    apiOn = v
                    YuhengPrefs.setApi(context, v, apiKey)
                    report(YuhengCore.Result.Ok(if (v) "API 已启用" else "API 已停用"))
                }
            }
            Spacer(Modifier.height(6.dp))
            TextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    YuhengPrefs.setApi(context, apiOn, it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("API 密钥（≥8 位）") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "随机生成",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.clip(beidouShape(50)).background(accent.copy(alpha = 0.14f))
                        .clickable {
                            apiKey = YuhengPrefs.generateKey()
                            YuhengPrefs.setApi(context, apiOn, apiKey)
                            report(YuhengCore.Result.Ok("已生成新密钥"))
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "调用方式：广播 action=<包名>.action.<名称>，组件=<包名>/.feature.yuheng.YuhengApiReceiver，附带 key 参数。",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "支持 ${YuhengApiReceiver.ACTIONS.size} 个 action：" +
                    YuhengApiReceiver.ACTIONS.joinToString("、") { it.first },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.45f),
            )
        }

        YuhengSection("组织信息", accent) {
            YuhengInfoRow("组织名称", YuhengCore.organizationName(context))
            YuhengInfoRow("锁屏信息", YuhengCore.deviceOwnerLockScreenInfo(context))
            YuhengInfoRow("设备型号", "${Build.MANUFACTURER} ${Build.MODEL}")
            YuhengInfoRow("系统版本", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            YuhengInfoRow("权限", YuhengCore.privilegeSummary(context))
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ═════════════════════ 专用子页 ═════════════════════

@Composable
private fun YuhengSubPage(id: String, accent: Color, onReport: (YuhengCore.Result) -> Unit) {
    when (id) {
        "ca_certificates" -> CaCertPage(accent, onReport)
        "security_log" -> SecurityLogPage(accent, onReport)
        "device_info" -> DeviceInfoPage(accent)
        "lock_task" -> LockTaskPage(accent, onReport)
        "hardware_monitor" -> HardwareMonitorPage(accent)
        "account_management" -> AccountManagementPage(accent, onReport)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("该功能页开发中", color = StarWhite.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CaCertPage(accent: Color, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val certs = remember(tick) { YuhengOps.installedCaCerts(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("已安装 CA 证书（${certs.size}）", accent) {
            if (certs.isEmpty()) {
                Text("未安装用户 CA 证书", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.45f))
            } else {
                certs.forEach { YuhengInfoRow("证书", it) }
            }
        }
        YuhengActionRow("卸载全部 CA 证书", "移除所有用户安装的 CA 证书", Color(0xFFFF6B9D)) {
            report(YuhengOps.uninstallAllCaCerts(context))
            tick++
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SecurityLogPage(accent: Color, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val enabled = remember(tick) { YuhengOps.securityLoggingEnabled(context) }
    val netEnabled = remember(tick) { YuhengOps.networkLoggingEnabled(context) }
    val netLog = remember(tick) { YuhengPrefs.networkLog(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("安全日志", accent) {
            YuhengToggleRow("启用安全日志", "记录设备安全事件（需 Android 8+）", enabled, accent) { v ->
                report(YuhengOps.setSecurityLogging(context, v))
                tick++
            }
            Text(
                "开启后系统会记录密码变更、应用安装、权限变更等事件；受系统批量限制，通常需等待约一小时才可拉取。",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.5f),
            )
        }
        YuhengSection("网络日志", accent) {
            YuhengToggleRow("启用网络日志", "记录 DNS 解析与连接事件", netEnabled, accent) { v ->
                report(YuhengOps.setNetworkLogging(context, v))
                tick++
            }
            YuhengInfoRow("已缓存日志", "${netLog.length} 字符")
            if (netLog.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    netLog.takeLast(1500),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = StarWhite.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "清空日志",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6B9D),
                    modifier = Modifier.clip(beidouShape(50)).background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                        .clickable { YuhengPrefs.clearNetworkLog(context); tick++ }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DeviceInfoPage(accent: Color) {
    val context = LocalContext.current
    val d = remember { YuhengCore.dpm(context) }
    val a = remember { YuhengCore.admin(context) }

    YuhengSection("设备与策略信息", accent) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            YuhengInfoRow("设备所有者", if (YuhengCore.isDeviceOwner(context)) "本应用" else "否")
            YuhengInfoRow("资料所有者", if (YuhengCore.isProfileOwner(context)) "本应用" else "否")
            YuhengInfoRow("存储加密", runCatching { d.storageEncryptionStatus.toString() }.getOrDefault("—"))
            YuhengInfoRow(
                "活动管理员",
                runCatching { d.activeAdmins?.joinToString("\n") { it.flattenToShortString() } ?: "—" }.getOrDefault("—"),
            )
            YuhengInfoRow("组织 ID", runCatching { if (Build.VERSION.SDK_INT >= 31) d.enrollmentSpecificId ?: "" else "" }.getOrDefault(""))
            YuhengInfoRow("附属用户", runCatching { if (Build.VERSION.SDK_INT >= 28 && d.isAffiliatedUser) "是" else "否" }.getOrDefault("否"))
            YuhengInfoRow("已授权 CA 数", YuhengOps.installedCaCerts(context).size.toString())
            YuhengInfoRow("锁任务白名单", runCatching { (d.getLockTaskPackages(a) ?: emptyArray()).joinToString("\n") }.getOrDefault("—"))
        }
    }
}

@Composable
private fun LockTaskPage(accent: Color, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var pkgInput by remember { mutableStateOf("") }
    val packages = remember(tick) {
        runCatching { YuhengCore.dpm(context).getLockTaskPackages(YuhengCore.admin(context))?.toList() ?: emptyList() }
            .getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("锁定任务模式（Kiosk）", accent) {
            Text(
                "把应用加入白名单后，设备可被锁定为仅运行这些应用。功能位控制是否允许通知、返回主页等。",
                style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
            YuhengInfoRow("当前白名单", if (packages.isEmpty()) "（空）" else packages.joinToString("\n"))
        }
        YuhengSection("维护白名单", accent) {
            TextField(
                value = pkgInput,
                onValueChange = { pkgInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("包名，如 com.example.app") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "添加",
                    style = MaterialTheme.typography.labelSmall, color = accent,
                    modifier = Modifier.clip(beidouShape(50)).background(accent.copy(alpha = 0.14f))
                        .clickable {
                            if (pkgInput.isNotBlank()) {
                                report(YuhengCore.call("添加白名单") {
                                    YuhengCore.dpm(context).setLockTaskPackages(
                                        YuhengCore.admin(context), (packages + pkgInput.trim()).toTypedArray(),
                                    )
                                })
                                pkgInput = ""
                                tick++
                            }
                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    "清空白名单",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6B9D),
                    modifier = Modifier.clip(beidouShape(50)).background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                        .clickable {
                            report(YuhengCore.call("清空白名单") {
                                YuhengCore.dpm(context).setLockTaskPackages(YuhengCore.admin(context), emptyArray())
                            })
                            tick++
                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HardwareMonitorPage(accent: Color) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { kotlinx.coroutines.delay(1500); tick++ }

    val temps = remember(tick) {
        runCatching {
            if (Build.VERSION.SDK_INT < 24) emptyList()
            else {
                val hpm = context.getSystemService(android.os.HardwarePropertiesManager::class.java)
                listOf(
                    "CPU" to android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    "GPU" to android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                    "电池" to android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                    "外壳" to android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                ).mapNotNull { (name, type) ->
                    val v = runCatching {
                        hpm.getDeviceTemperatures(type, android.os.HardwarePropertiesManager.TEMPERATURE_CURRENT)
                    }.getOrNull()
                    v?.firstOrNull()?.let { name to "%.1f °C".format(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    YuhengSection("硬件监控（每 1.5 秒刷新）", accent) {
        if (temps.isEmpty()) {
            Text("系统未开放硬件温度接口（需设备所有者且部分机型不支持）", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
        } else {
            temps.forEach { (k, v) -> YuhengInfoRow(k, v) }
        }
    }
}

@Composable
private fun AccountManagementPage(accent: Color, report: (YuhengCore.Result) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    val disabled = remember(tick) {
        runCatching { YuhengCore.dpm(context).accountTypesWithManagementDisabled?.toList() ?: emptyList() }
            .getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        YuhengSection("已禁用管理的账号类型", accent) {
            if (disabled.isEmpty()) {
                Text("（空）", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.45f))
            } else {
                disabled.forEach { YuhengInfoRow("账号类型", it) }
            }
        }
        YuhengSection("添加账号类型", accent) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("如 com.google") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "禁用管理",
                style = MaterialTheme.typography.labelSmall, color = accent,
                modifier = Modifier.clip(beidouShape(50)).background(accent.copy(alpha = 0.14f))
                    .clickable {
                        if (input.isNotBlank()) {
                            report(YuhengCore.call("禁用账号管理") {
                                YuhengCore.dpm(context).setAccountManagementDisabled(
                                    YuhengCore.admin(context), input.trim(), true,
                                )
                            })
                            input = ""
                            tick++
                        }
                    }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
