package org.beidou.stareye.feature.yaoguang

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.beidou.stareye.core.freeze.FreezeState
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.DeepSpaceLight
import org.beidou.stareye.ui.theme.StarWhite

/**
 * 瑶光中枢的星空玻璃 UI 原语（与玉衡 / 天璇同一视觉语言）。
 */

@Composable
fun YaoguangSection(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(8.dp))
        content()
    }
    Spacer(Modifier.height(12.dp))
}

/** 条目外壳：标题 + 说明 + 右侧控件 */
@Composable
private fun EntryShell(title: String, detail: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = StarWhite,
            )
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun YaoguangToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
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
fun YaoguangActionRow(
    title: String,
    detail: String,
    color: Color,
    enabled: Boolean = true,
    action: String = "执行",
    onClick: () -> Unit,
) {
    EntryShell(title, detail) {
        Text(
            action,
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
fun YaoguangEnumRow(
    title: String,
    detail: String = "",
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
                Column {
                    options.forEachIndexed { index, option ->
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == selectedIndex) color else StarWhite.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(index); open = false }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
            containerColor = DeepSpaceLight,
        )
    }
}

@Composable
fun YaoguangChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
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

@Composable
fun YaoguangSearchField(
    value: String,
    placeholder: String,
    accent: Color,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = StarWhite),
        cursorBrush = SolidColor(accent),
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarWhite.copy(alpha = 0.4f),
                    )
                }
                inner()
            }
        },
    )
}

/** 应用图标：冻结时灰化，隐藏 / 冻结角标 */
@Composable
fun YaoguangAppIcon(app: YaoguangApp, size: Dp = 44.dp) {
    Box(Modifier.size(size)) {
        val bitmap = app.icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(size / 4)),
                colorFilter = if (app.frozen) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(size / 4))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.label.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = StarWhite.copy(alpha = 0.7f),
                )
            }
        }
        val badge = when {
            !app.installed -> "隐"
            app.frozen -> "❄"
            else -> ""
        }
        if (badge.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color(0xCC0A0F24))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = StarWhite)
            }
        }
    }
}

/** 冻结状态小标签（正常状态返回 null，不占位） */
@Composable
fun YaoguangStateBadge(app: YaoguangApp, accent: Color) {
    if (app.flags.kind == FreezeState.Kind.NONE) return
    val color = if (app.frozen) accent else StarWhite.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            app.stateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
