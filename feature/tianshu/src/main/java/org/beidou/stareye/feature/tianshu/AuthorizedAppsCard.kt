package org.beidou.stareye.feature.tianshu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 已授权应用列表：每个应用一个开关，控制是否授予其天枢（Shizuku）权限。
 */
@Composable
fun AuthorizedAppsCard(
    apps: List<TianshuApp>,
    granted: Map<Int, Boolean>,
    onToggle: (TianshuApp, Boolean) -> Unit,
) {
    val accent = starAccent(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "✦ 应用授权管理",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = "开启开关即授予该应用天枢（Shizuku）权限，关闭则吊销。",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.5f),
        )

        if (apps.isEmpty()) {
            Text(
                text = "暂无应用申请天枢授权",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.45f),
            )
            return@Column
        }

        apps.forEach { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    StarGlyph(color = accent, size = 28.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StarWhite,
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = StarWhite.copy(alpha = 0.4f),
                    )
                }
                Switch(
                    checked = granted[app.uid] ?: false,
                    onCheckedChange = { onToggle(app, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent,
                        checkedTrackColor = accent.copy(alpha = 0.35f),
                        uncheckedThumbColor = StarWhite.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )
            }
        }
    }
}

/**
 * 不断变色的文本（用于 root 授权等级的「至高无上」提示）。
 */
@Composable
fun RainbowText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rainbow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3000, easing = LinearEasing)),
        label = "hue",
    )
    val hue = (progress * 360f) % 360f
    Text(
        text = text,
        style = style,
        modifier = modifier,
        color = Color.hsv(hue, 0.75f, 1f),
    )
}

/** 授权等级对应的展示文本 */
fun authLevelText(level: AuthLevel): String = when (level) {
    AuthLevel.ROOT -> "已获得root授权，这是至高无上的权力！！！"
    AuthLevel.ADB -> "已获得adb授权"
    AuthLevel.UNKNOWN -> "已获得授权"
    AuthLevel.NONE -> "未授权"
}

/** 授权等级颜色（root 用动态彩虹，见 [RainbowText]） */
fun authLevelColor(level: AuthLevel): Color = when (level) {
    AuthLevel.ROOT -> Color(0xFFFF6B9D)
    AuthLevel.ADB -> StarBlue
    AuthLevel.UNKNOWN -> StarWhite
    AuthLevel.NONE -> StarWhite.copy(alpha = 0.5f)
}
