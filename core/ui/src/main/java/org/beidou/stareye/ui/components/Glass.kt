package org.beidou.stareye.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.beidou.stareye.core.model.FeatureStatus
import org.beidou.stareye.ui.theme.GlassTint
import org.beidou.stareye.ui.theme.StarBlue

/**
 * 液态玻璃 / 毛玻璃表面修饰符：
 * 外发光阴影 + 半透明渐变底 + 高光描边，营造通透的玻璃质感。
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = GlassTint,
): Modifier = this
    .shadow(
        elevation = 24.dp,
        shape = shape,
        ambientColor = StarBlue.copy(alpha = 0.10f),
        spotColor = Color.White.copy(alpha = 0.16f),
    )
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                tint,
                Color.White.copy(alpha = 0.05f),
            ),
            start = Offset.Zero,
            end = Offset.Infinite,
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.35f),
            ),
        ),
        shape = shape,
    )

/**
 * 五角星形（用于星位标识）。
 */
@Composable
fun StarGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val innerR = r * 0.42f
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else innerR
            val angle = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/**
 * 功能状态胶囊标签。
 */
@Composable
fun StatusChip(status: FeatureStatus, accent: Color) {
    val (text, color) = when (status) {
        FeatureStatus.AVAILABLE -> "已上线" to accent
        FeatureStatus.COMING_SOON -> "集成中" to Color.White.copy(alpha = 0.62f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
