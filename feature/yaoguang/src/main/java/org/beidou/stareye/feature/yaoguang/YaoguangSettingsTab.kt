package org.beidou.stareye.feature.yaoguang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.beidou.stareye.ui.theme.DeepSpaceLight
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite

private val DELAY_OPTIONS = listOf(0, 1, 5, 10, 15, 30)

private fun delayLabel(minutes: Int): String = if (minutes == 0) "息屏后立即" else "息屏后 $minutes 分钟"

/**
 * 瑶光中枢设置页：冻结方式 / 通道、自动冻结、搜索、标签、快捷方式与磁贴、对外 API、操作记录。
 */
@Composable
fun YaoguangSettingsTab(context: Context, accent: Color, onReport: (String) -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var logOpen by remember { mutableStateOf(false) }
    var newTagOpen by remember { mutableStateOf(false) }

    fun refresh() {
        revision++
    }

    val channel = remember(revision) { YaoguangStore.channel(context) }
    val kind = remember(revision) { YaoguangStore.kind(context) }
    val channelReady = remember(revision, channel, kind) { YaoguangCore.resolve(context, channel, kind) != null }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

        // ── 冻结方式与通道 ──
        YaoguangSection("冻结方式", accent) {
            YaoguangEnumRow(
                title = "方式",
                detail = kind.detail,
                options = FreezeKind.entries.map { it.label },
                selectedIndex = kind.ordinal,
                color = accent,
            ) { index ->
                YaoguangStore.setKind(context, FreezeKind.entries[index])
                onReport("冻结方式：${FreezeKind.entries[index].label}")
                refresh()
            }
            YaoguangEnumRow(
                title = "执行通道",
                detail = channel.detail,
                options = FreezeChannel.entries.map { it.label },
                selectedIndex = channel.ordinal,
                color = accent,
            ) { index ->
                YaoguangStore.setChannel(context, FreezeChannel.entries[index])
                onReport("执行通道：${FreezeChannel.entries[index].label}")
                refresh()
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    if (channelReady) "当前生效：${YaoguangCore.describe(context, channel, kind)}"
                    else "当前组合不可用：请激活开阳 / 天枢 / Root，或改用可执行的冻结方式",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (channelReady) StarWhite.copy(alpha = 0.7f) else StarGold,
                )
            }
        }

        // ── 自动冻结 ──
        YaoguangSection("自动冻结", accent) {
            YaoguangToggleRow(
                title = "息屏后自动冻结",
                detail = "常驻前台服务，息屏后冻结清单内应用，亮屏自动解冻",
                checked = YaoguangStore.autoFreeze(context),
                accent = accent,
            ) { enabled ->
                YaoguangStore.setAutoFreeze(context, enabled)
                YaoguangAutoFreezeService.setEnabled(context, enabled)
                onReport(if (enabled) "自动冻结已开启" else "自动冻结已关闭")
                refresh()
            }
            val delay = YaoguangStore.autoFreezeDelay(context)
            YaoguangEnumRow(
                title = "延迟",
                detail = delayLabel(delay),
                options = DELAY_OPTIONS.map { delayLabel(it) },
                selectedIndex = DELAY_OPTIONS.indexOf(delay).coerceAtLeast(0),
                color = accent,
            ) { index ->
                YaoguangStore.setAutoFreezeDelay(context, DELAY_OPTIONS[index])
                refresh()
            }
            YaoguangToggleRow(
                title = "充电时跳过",
                detail = "正在充电时不执行自动冻结",
                checked = YaoguangStore.skipWhileCharging(context),
                accent = accent,
            ) { YaoguangStore.setSkipWhileCharging(context, it); refresh() }
            YaoguangToggleRow(
                title = "跳过正在使用的应用",
                detail = "需授予「用量访问」权限",
                checked = YaoguangStore.skipForegroundApp(context),
                accent = accent,
            ) { YaoguangStore.setSkipForegroundApp(context, it); refresh() }
            if (!YaoguangOps.hasUsageAccess(context)) {
                YaoguangActionRow(
                    title = "授予用量访问",
                    detail = "未授权时该条件不生效",
                    color = StarGold,
                    action = "去设置",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            YaoguangToggleRow(
                title = "跳过有通知的应用",
                detail = "需授予「通知使用权」",
                checked = YaoguangStore.skipNotifyingApp(context),
                accent = accent,
            ) { YaoguangStore.setSkipNotifyingApp(context, it); refresh() }
            YaoguangActionRow(
                title = "通知使用权",
                detail = "在系统设置中勾选本应用后生效",
                color = StarGold,
                action = "去设置",
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        // ── 搜索 ──
        YaoguangSection("搜索", accent) {
            YaoguangToggleRow(
                title = "模糊搜索",
                detail = "查询字符按序出现即命中（如 wchat → WeChat）",
                checked = YaoguangStore.fuzzySearch(context),
                accent = accent,
            ) { YaoguangStore.setFuzzySearch(context, it); refresh() }
            YaoguangToggleRow(
                title = "九键搜索",
                detail = "纯数字查询按 T9 映射匹配应用名拼音",
                checked = YaoguangStore.nineKeySearch(context),
                accent = accent,
            ) { YaoguangStore.setNineKeySearch(context, it); refresh() }
        }

        // ── 标签 ──
        YaoguangSection("标签", accent) {
            YaoguangStore.tags(context).forEach { tag ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tag.name, style = MaterialTheme.typography.bodyMedium, color = StarWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (tag.id != DEFAULT_TAG) {
                            Text(
                                "删除",
                                style = MaterialTheme.typography.labelMedium,
                                color = StarGold,
                                modifier = Modifier.clickable {
                                    YaoguangStore.removeTag(context, tag.id)
                                    onReport("已删除标签「${tag.name}」")
                                    refresh()
                                },
                            )
                        }
                        Text(
                            "冻结",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.clickable {
                                onReport(YaoguangOps.setTagFrozen(context, tag.id, true).text)
                            },
                        )
                        Text(
                            "解冻",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.clickable {
                                onReport(YaoguangOps.setTagFrozen(context, tag.id, false).text)
                            },
                        )
                        Text(
                            "固定到桌面",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.clickable {
                                YaoguangShortcuts.requestPinTag(context, tag, frozen = true)
                                onReport("已请求固定「${tag.name}·冻结」快捷方式")
                            },
                        )
                    }
                }
            }
            YaoguangActionRow(title = "新建标签", detail = "用于首页分页与批量冻结", color = accent, action = "新建") {
                newTagOpen = true
            }
        }

        // ── 快捷方式与磁贴 ──
        YaoguangSection("快捷方式与磁贴", accent) {
            val dynamic = YaoguangStore.dynamicShortcutAction(context)
            YaoguangEnumRow(
                title = "动态快捷方式",
                detail = "长按桌面图标时显示的动作",
                options = YaoguangShortcuts.DYNAMIC_ACTIONS.map { YaoguangShortcuts.label(it) },
                selectedIndex = YaoguangShortcuts.DYNAMIC_ACTIONS.indexOf(dynamic).coerceAtLeast(0),
                color = accent,
            ) { index ->
                YaoguangStore.setDynamicShortcutAction(context, YaoguangShortcuts.DYNAMIC_ACTIONS[index])
                YaoguangShortcuts.refreshDynamic(context)
                onReport("动态快捷方式：${YaoguangShortcuts.label(YaoguangShortcuts.DYNAMIC_ACTIONS[index])}")
                refresh()
            }
            YaoguangShortcuts.PIN_ACTIONS.forEach { action ->
                YaoguangActionRow(
                    title = YaoguangShortcuts.label(action),
                    detail = "添加为桌面固定快捷方式",
                    color = accent,
                    action = "固定",
                ) {
                    YaoguangShortcuts.requestPin(context, action)
                    onReport("已请求固定「${YaoguangShortcuts.label(action)}」")
                }
            }
            val tileAction = YaoguangStore.tileAction(context)
            YaoguangEnumRow(
                title = "磁贴动作",
                detail = "下拉快捷设置中的磁贴点击行为",
                options = YaoguangShortcuts.TILE_ACTIONS.map { YaoguangShortcuts.label(it) },
                selectedIndex = YaoguangShortcuts.TILE_ACTIONS.indexOf(tileAction).coerceAtLeast(0),
                color = accent,
            ) { index ->
                YaoguangStore.setTileAction(context, YaoguangShortcuts.TILE_ACTIONS[index])
                onReport("磁贴动作：${YaoguangShortcuts.label(YaoguangShortcuts.TILE_ACTIONS[index])}")
                refresh()
            }
        }

        // ── 对外 API ──
        YaoguangSection("对外 API", accent) {
            YaoguangToggleRow(
                title = "启用对外调用",
                detail = "允许其他应用 / 自动化工具以广播或意图调用本中枢",
                checked = YaoguangStore.apiEnabled(context),
                accent = accent,
            ) { YaoguangStore.setApiEnabled(context, it); refresh() }
            YaoguangToggleRow(
                title = "兼容雹（Hail）契约",
                detail = "接受 com.aistra.hail.action.* 意图，既有自动化脚本无需改动；若设备上装有雹原版，系统会弹出应用选择器",
                checked = YaoguangStore.hailCompat(context),
                accent = accent,
            ) { YaoguangStore.setHailCompat(context, it); refresh() }

            val key = YaoguangStore.apiKey(context)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    if (key.isEmpty()) "当前无需密钥（任何应用均可调用）" else "当前密钥：$key",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (key.isEmpty()) StarGold else StarWhite.copy(alpha = 0.8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YaoguangChip("生成密钥", false, accent) {
                    val generated = YaoguangStore.generateApiKey()
                    YaoguangStore.setApiKey(context, generated)
                    copyToClipboard(context, generated)
                    onReport("已生成并复制密钥")
                    refresh()
                }
                YaoguangChip("复制密钥", key.isNotEmpty(), accent) {
                    copyToClipboard(context, key)
                    onReport("已复制密钥")
                }
                YaoguangChip("清除密钥", key.isNotEmpty(), StarGold) {
                    YaoguangStore.setApiKey(context, "")
                    onReport("已清除密钥（恢复为免密钥调用）")
                    refresh()
                }
            }
            Text(
                "广播示例：adb shell am broadcast -a ${context.packageName}.action.FREEZE_ALL",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ── 操作记录 ──
        YaoguangSection("操作记录", accent) {
            val logs = remember(revision) { YaoguangStore.logs(context) }
            Text(
                "共 ${logs.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.6f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YaoguangChip("查看", logs.isNotEmpty(), accent) { logOpen = true }
                YaoguangChip("清空", logs.isNotEmpty(), StarGold) {
                    YaoguangStore.clearLogs(context)
                    onReport("已清空操作记录")
                    refresh()
                }
            }
        }
    }

    if (newTagOpen) {
        TagNameDialog(
            onDismiss = { newTagOpen = false },
            onConfirm = { name ->
                YaoguangStore.addTag(context, name)
                onReport("已新建标签「$name」")
                newTagOpen = false
                refresh()
            },
        )
    }

    if (logOpen) {
        val logs = YaoguangStore.logs(context)
        AlertDialog(
            onDismissRequest = { logOpen = false },
            title = { Text("操作记录") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    logs.take(80).forEach { log ->
                        Text(
                            "${timeText(log.time)}  ${log.action}  ${log.target}  ${log.result}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = StarWhite.copy(alpha = 0.85f),
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { logOpen = false }) { Text("关闭") } },
            containerColor = DeepSpaceLight,
        )
    }
}

@Composable
private fun TagNameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            Column {
                YaoguangSearchField(name, "标签名称", Color.White.copy(alpha = 0.8f)) { name = it }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("工作", "游戏", "社交", "工具").forEach { preset ->
                        YaoguangChip(preset, name == preset, StarWhite) { name = preset }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = DeepSpaceLight,
    )
}

private fun timeText(millis: Long): String {
    val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return format.format(java.util.Date(millis))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("yaoguang", text))
}
