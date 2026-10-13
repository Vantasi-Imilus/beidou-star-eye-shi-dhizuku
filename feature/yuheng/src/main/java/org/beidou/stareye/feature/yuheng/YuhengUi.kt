package org.beidou.stareye.feature.yuheng

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite

/**
 * 玉衡中枢 —— 通用界面组件。
 *
 * 功能目录里的每个条目都由这里的通用渲染器呈现，
 * 因而新增功能只需改数据表，无需另写界面。
 */

@Composable
fun YuhengSection(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(8.dp))
        content()
    }
    Spacer(Modifier.height(12.dp))
}

/** 条目卡片外壳：标题 + 说明 + 右侧控件 */
@Composable
private fun EntryShell(
    title: String,
    detail: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = StarWhite)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun YuhengToggleRow(title: String, detail: String, checked: Boolean, accent: Color, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    EntryShell(title, detail) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.35f),
                uncheckedThumbColor = StarWhite.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
fun YuhengActionRow(title: String, detail: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    EntryShell(title, detail) {
        Text(
            "执行",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) color else StarWhite.copy(alpha = 0.3f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = if (enabled) 0.16f else 0.06f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun YuhengNavRow(title: String, detail: String, color: Color, badge: String = "", onClick: () -> Unit) {
    EntryShell(title, badge.ifEmpty { detail }) {
        Text(
            "进入 ›",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** 枚举单选对话框 */
@Composable
fun YuhengEnumRow(
    title: String,
    detail: String,
    options: List<String>,
    selectedIndex: Int,
    color: Color,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.getOrNull(selectedIndex) ?: "—"

    EntryShell(title, detail.ifEmpty { "当前：$current" }) {
        Text(
            current,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (index == selectedIndex) color.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    onSelect(index)
                                    open = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (index == selectedIndex) "◉" else "○", color = if (index == selectedIndex) color else StarWhite.copy(alpha = 0.4f))
                            Spacer(Modifier.width(10.dp))
                            Text(option, style = MaterialTheme.typography.bodyMedium, color = StarWhite)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
        )
    }
}

/** 文本输入对话框 */
@Composable
fun YuhengTextRow(
    title: String,
    detail: String,
    value: String,
    color: Color,
    placeholder: String = "",
    monospace: Boolean = false,
    onCommit: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }

    EntryShell(title, detail.ifEmpty { value.ifBlank { "未设置" } }) {
        Text(
            "编辑",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .clickable { text = value; open = true }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder.ifEmpty { detail }, color = StarWhite.copy(alpha = 0.3f)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCommit(text)
                    open = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

/**
 * 通用条目渲染：按 [FeatureEntry.kind] 分派到对应控件。
 *
 * @param before 渲染前的钩子，返回 false 表示隐藏该条目（如权限不满足）
 */
@Composable
fun YuhengFeatureRenderer(
    entry: FeatureEntry,
    accent: Color,
    ownerReady: Boolean,
    showDangerous: Boolean,
    isChecked: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    onAction: (String) -> Unit,
    onScreen: (String) -> Unit,
    enumIndex: (String) -> Int,
    enumOptions: (String) -> List<String>,
    onEnum: (String, Int) -> Unit,
    textValue: (String) -> String,
    onText: (String, String) -> Unit,
) {
    // 权限/版本不符则隐藏
    if (entry.minApi > android.os.Build.VERSION.SDK_INT) return
    if (entry.deviceOnly && !YuhengCore.isDeviceOwner(androidx.compose.ui.platform.LocalContext.current)) return
    if (entry.danger && !showDangerous) return

    val dangerColor = if (entry.danger) Color(0xFFFF6B9D) else accent

    when (entry.kind) {
        EntryKind.TOGGLE -> YuhengToggleRow(
            entry.title, entry.detail, isChecked(entry.id), dangerColor, enabled = ownerReady,
        ) { onToggle(entry.id, it) }

        EntryKind.ACTION -> YuhengActionRow(
            entry.title, entry.detail, dangerColor, enabled = ownerReady,
        ) { onAction(entry.id) }

        EntryKind.ENUM -> YuhengEnumRow(
            entry.title, entry.detail, enumOptions(entry.id), enumIndex(entry.id), dangerColor,
        ) { onEnum(entry.id, it) }

        EntryKind.TEXT -> YuhengTextRow(
            entry.title, entry.detail, textValue(entry.id), dangerColor,
        ) { onText(entry.id, it) }

        EntryKind.SCREEN -> YuhengNavRow(
            entry.title, entry.detail, dangerColor,
        ) { onScreen(entry.id) }
    }
}

/** 顶栏下方的标签条 */
@Composable
fun YuhengTabChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else StarWhite.copy(alpha = 0.6f),
            maxLines = 1,
        )
    }
}

/** 简单信息行 */
@Composable
fun YuhengInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.55f), modifier = Modifier.width(120.dp))
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = StarWhite.copy(alpha = 0.88f),
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun YuhengDangerRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(StarGold.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = StarGold)
    }
}
