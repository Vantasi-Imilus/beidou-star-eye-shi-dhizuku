package org.beidou.stareye.feature.fanxiang

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.core.shell.ShizukuShell
import org.beidou.stareye.feature.kaiyang.KaiyangState
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.beidouShape
import org.beidou.stareye.ui.theme.starAccent

/**
 * 反向星界 —— 七中枢的星界映射页。
 *
 * 星界（镜像空间）中映射七中枢的能力：作用于**星界内部的应用与空间**，
 * 其中「开阳中枢」在星界语境下更名「星界中枢」（管理星界自身的生命周期）。
 */

/** 页面通用骨架：返回栏 + 内容 */
@Composable
private fun RealmPageScaffold(
    title: String,
    origin: String,
    accent: Color,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Text(
                    "‹ 返回",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .clip(beidouShape(10.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    "星界映射 · 源自$origin",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.5f),
                )
            }
        }
        content()
    }
}

/** 单行信息 */
@Composable
private fun RealmInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.55f), modifier = Modifier.width(96.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = StarWhite.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
        )
    }
}

/** 通用操作按钮 */
@Composable
private fun RealmButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(beidouShape(14.dp))
            .background(color.copy(alpha = if (enabled) 0.16f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
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

// ═════════════ 星界中枢（原开阳中枢）═════════════

@Composable
fun RealmCorePage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(6)
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmDestroy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    RealmPageScaffold("星界中枢", "开阳中枢", accent, onBack, modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().glass(shape = beidouShape(20.dp)).padding(16.dp)) {
                Text("✦ 空间状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.height(8.dp))
                RealmInfoRow("状态", RealmState.describe(context))
                RealmInfoRow("空间形态", RealmState.mode(context).label)
                RealmInfoRow("管理权", if (hasProfileOwner(context)) "本应用（Profile Owner）" else "未持有")
                RealmInfoRow("设备所有者", if (RealmState.ownerReady(context)) "已激活（开阳）" else "未激活")
            }
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
            }
            if (RealmState.exists(context)) {
                RealmButton("湮灭星界（移除全部数据）", Color(0xFFFF6B9D), enabled = !busy) {
                    confirmDestroy = true
                }
            } else {
                RealmButton("开辟反向星界", accent, enabled = !busy) {
                    scope.launch {
                        busy = true
                        message = withContext(Dispatchers.IO) {
                            RealmState.create(context).fold({ it }, { "失败：${it.message}" })
                        }
                        busy = false
                        refresh++
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDestroy) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDestroy = false },
            title = { Text("湮灭反向星界") },
            text = { Text("将移除整个镜像空间及其中的全部应用与数据，不可恢复。确定继续吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDestroy = false
                    scope.launch {
                        busy = true
                        message = withContext(Dispatchers.IO) {
                            RealmState.destroy(context).fold({ "星系已湮灭" }, { "失败：${it.message}" })
                        }
                        busy = false
                        refresh++
                    }
                }) { Text("湮灭", color = Color(0xFFFF6B9D)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDestroy = false }) { Text("取消") }
            },
            containerColor = org.beidou.stareye.ui.theme.DeepSpaceLight,
        )
    }
}

/** 本应用是否持有星界空间的管理权 */
private fun hasProfileOwner(context: Context): Boolean = runCatching {
    val user = RealmState.realmUser(context) ?: return false
    val profileContext = RealmHidden.contextAsUser(context, user) ?: return false
    val dpm = profileContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    dpm.isProfileOwnerApp(context.packageName)
}.getOrDefault(false)

// ═════════════ 天璇 · 星界应用管理 ═════════════

@Composable
fun RealmAppsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(2)
    var apps by remember { mutableStateOf<List<RealmApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var actionApp by remember { mutableStateOf<RealmApp?>(null) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(refreshKey) {
        loading = true
        apps = withContext(Dispatchers.IO) { RealmApps.list(context) }
        loading = false
    }
    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    fun run(label: String, block: () -> Result<*>) {
        scope.launch {
            message = ""
            val result = withContext(Dispatchers.IO) { block() }
            message = result.fold(
                onSuccess = { (it as? String)?.takeIf { s -> s.isNotBlank() } ?: "$label · 完成" },
                onFailure = { "$label · ${it.message ?: "失败"}" },
            )
            refreshKey++
        }
    }

    RealmPageScaffold("逆·天璇中枢", "天璇中枢", accent, onBack, modifier) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)),
            placeholder = { Text("搜索星界应用…", color = StarWhite.copy(alpha = 0.35f)) },
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
        Spacer(Modifier.height(8.dp))
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
            Spacer(Modifier.height(6.dp))
        }
        if (loading) {
            Text("读取中…", style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.6f))
        } else if (visible.isEmpty()) {
            Text(
                "星界内暂无应用 —— 在「逆·天玑中枢」中从主界映照。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.55f),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(visible, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(16.dp))
                            .clickable { actionApp = app }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RealmAppIcon(app, accent, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium, color = StarWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = StarWhite.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (app.frozen) Text("❄", color = StarGold)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    actionApp?.let { app ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { actionApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    realmAction("启动" + if (app.frozen) "（自动解冻）" else "") {
                        actionApp = null
                        run("启动") { RealmApps.launch(context, app).map { } }
                    }
                    realmAction(if (app.frozen) "解冻" else "冻结") {
                        actionApp = null
                        run(if (app.frozen) "解冻" else "冻结") { RealmApps.setFrozen(context, app.packageName, !app.frozen) }
                    }
                    realmAction("强停（天璇能力）") {
                        actionApp = null
                        run("强停") { RealmApps.forceStop(context, app.packageName) }
                    }
                    realmAction("从星界移除") {
                        actionApp = null
                        run("移除") { RealmApps.remove(context, app.packageName) }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { actionApp = null }) { Text("关闭") }
            },
            containerColor = org.beidou.stareye.ui.theme.DeepSpaceLight,
        )
    }
}

@Composable
private fun realmAction(text: String, onClick: () -> Unit) {
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

// ═════════════ 天玑 · 映照安装 ═════════════

@Composable
fun RealmInstallPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(3)
    var apps by remember { mutableStateOf<List<RealmApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        apps = withContext(Dispatchers.IO) { RealmApps.mirrorable(context) }
    }
    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    RealmPageScaffold("逆·天玑中枢", "天玑中枢", accent, onBack, modifier) {
        Text(
            "把主界的应用映照（复制）进星界 —— 无需下载，经系统直接复制已安装的应用。",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)),
            placeholder = { Text("搜索主界应用…", color = StarWhite.copy(alpha = 0.35f)) },
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
        Spacer(Modifier.height(8.dp))
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(visible.take(300), key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(16.dp))
                        .clickable {
                            scope.launch {
                                message = "正在映照 ${app.label}…"
                                val result = withContext(Dispatchers.IO) { RealmApps.mirror(context, app.packageName) }
                                message = result.fold({ "已映照 ${app.label}" }, { "失败：${it.message}" })
                                refreshKey++
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealmAppIcon(app, accent, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodySmall, color = StarWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = StarWhite.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("映照", style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ═════════════ 瑶光 · 批量冻结 ═════════════

@Composable
fun RealmFreezePage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(7)
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun batch(frozen: Boolean) {
        scope.launch {
            busy = true
            message = if (frozen) "正在冻结星界应用…" else "正在解冻星界应用…"
            val result = withContext(Dispatchers.IO) {
                val apps = RealmApps.list(context)
                var ok = 0
                var fail = 0
                apps.forEach { app ->
                    RealmApps.setFrozen(context, app.packageName, frozen)
                        .fold({ ok++ }, { fail++ })
                }
                "完成：成功 $ok 个" + if (fail > 0) "，失败 $fail 个" else ""
            }
            message = result
            busy = false
        }
    }

    RealmPageScaffold("逆·瑶光中枢", "瑶光中枢", accent, onBack, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "对星界内的全部应用执行冻结 / 解冻（与瑶光中枢同一冻结语义，作用域为星界空间）。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.7f),
            )
            RealmButton("冻结全部星界应用", accent, enabled = !busy) { batch(true) }
            RealmButton("解冻全部星界应用", starAccent(2), enabled = !busy) { batch(false) }
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ═════════════ 天权 · 星界终端 ═════════════

@Composable
fun RealmTerminalPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(4)
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val userId = RealmState.realmUser(context)?.let { RealmState.userIdOf(it) } ?: -1

    RealmPageScaffold("逆·天权中枢", "天权中枢", accent, onBack, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "以天枢 shell 身份执行命令，作用于星界空间（user $userId）。" +
                    "示例：pm list packages --user $userId",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.6f),
            )
            TextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)),
                placeholder = { Text("输入 shell 命令…", color = StarWhite.copy(alpha = 0.35f)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
            RealmButton(if (busy) "执行中…" else "执行", accent, enabled = !busy && command.isNotBlank()) {
                scope.launch {
                    busy = true
                    output = withContext(Dispatchers.IO) {
                        ShizukuShell.run(command).fold(
                            { it.take(4000).ifBlank { "(无输出)" } },
                            { "错误：${it.message}" },
                        )
                    }
                    busy = false
                }
            }
            output?.let {
                Column(
                    Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)).padding(12.dp)
                        .height(260.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = StarWhite.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

// ═════════════ 玉衡 · 空间限制 ═════════════

private data class RealmRestriction(val key: String, val label: String)

private val REALM_RESTRICTIONS = listOf(
    RealmRestriction(UserManager.DISALLOW_INSTALL_APPS, "禁止安装应用"),
    RealmRestriction(UserManager.DISALLOW_UNINSTALL_APPS, "禁止卸载应用"),
    RealmRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, "禁止未知来源安装"),
    RealmRestriction(UserManager.DISALLOW_CONFIG_WIFI, "禁止配置 Wi-Fi"),
    RealmRestriction("no_camera", "禁用相机"),
    RealmRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, "禁止 USB 文件传输"),
    RealmRestriction(UserManager.DISALLOW_SHARE_LOCATION, "禁止共享位置"),
)

@Composable
fun RealmRestrictPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var states by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var message by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        states = withContext(Dispatchers.IO) {
            val user = RealmState.realmUser(context)
            if (user == null) emptyMap() else RealmHidden.restrictionStates(context, user, REALM_RESTRICTIONS.map { it.key })
        }
    }

    RealmPageScaffold("逆·玉衡中枢", "玉衡中枢", starAccent(5), onBack, modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "对星界空间施加系统级限制（本应用持有该空间管理权，经 DevicePolicyManager 生效）。",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.6f),
            )
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.85f))
            }
            REALM_RESTRICTIONS.forEach { restriction ->
                Row(
                    modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        restriction.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarWhite,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = states[restriction.key] == true,
                        onCheckedChange = { enabled ->
                            val result = RealmHidden.setRestriction(context, restriction.key, enabled)
                            message = result.fold(
                                { "${restriction.label} · ${if (enabled) "已开启" else "已关闭"}" },
                                { "失败：${it.message}" },
                            )
                            refreshKey++
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = starAccent(5),
                            checkedTrackColor = starAccent(5).copy(alpha = 0.3f),
                            uncheckedThumbColor = StarWhite.copy(alpha = 0.5f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ═════════════ 天枢 · 通道状态 ═════════════

@Composable
fun RealmChannelPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = starAccent(1)
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ready = ShizukuShell.ready() }

    RealmPageScaffold("逆·天枢中枢", "天枢中枢", accent, onBack, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().glass(shape = beidouShape(20.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarGlyph(color = if (ready) accent else Color.Gray, size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("天枢（Shizuku）通道", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StarWhite)
                }
                Spacer(Modifier.height(8.dp))
                RealmInfoRow("状态", if (ready) "已就绪" else "未就绪")
                RealmInfoRow("宿权身份", if (ready) "shell（uid 2000）" else "—")
            }
            Text(
                "星界的开辟、应用管理、终端等功能经天枢的 shell 通道执行。" +
                    "若显示未就绪，请在「天枢中枢」中激活（ADB / Root）。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.7f),
            )
            RealmButton("重新检测", accent) { ready = ShizukuShell.ready() }
            Spacer(Modifier.height(16.dp))
        }
    }
}
