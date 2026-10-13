package org.beidou.stareye.ui.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.beidou.stareye.ui.theme.DeepSpace
import org.beidou.stareye.ui.theme.NebulaBlue
import org.beidou.stareye.ui.theme.NebulaCyan
import org.beidou.stareye.ui.theme.NebulaPurple
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite

/**
 * 星空主题背景：深空渐变 + 漂移星云 + 光柱（光线追踪感）+ 闪烁繁星 +
 * 流星 + 北斗七星星座。
 */
@Composable
fun StarField(modifier: Modifier = Modifier) {
    val stars = remember { generateStars(240, seed = 7L) }
    val transition = rememberInfiniteTransition(label = "starfield")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 16_000, easing = LinearEasing)),
        label = "time",
    )

    Canvas(modifier = modifier) {
        // 深空底色
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(DeepSpace, Color(0xFF020308), DeepSpace),
            ),
        )
        drawNebulae(time)
        drawGodRays(time)
        drawStars(stars, time)
        drawShootingStar(time)
        drawDipper(time)
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val baseAlpha: Float,
    val speed: Float,
    val phase: Float,
    val warm: Boolean,
)

private fun generateStars(count: Int, seed: Long): List<Star> {
    val rnd = Random(seed)
    return List(count) {
        Star(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            radius = 0.6f + rnd.nextFloat() * 2.0f,
            baseAlpha = 0.25f + rnd.nextFloat() * 0.75f,
            speed = 0.4f + rnd.nextFloat() * 1.6f,
            phase = rnd.nextFloat(),
            warm = rnd.nextFloat() > 0.75f,
        )
    }
}

private fun DrawScope.drawNebulae(time: Float) {
    val w = size.width
    val h = size.height
    val t = time * 2f * PI.toFloat()
    nebula(NebulaPurple, w * 0.22f + sin(t) * 24f, h * 0.30f + cos(t * 0.7f) * 18f, w * 0.55f, 0.30f)
    nebula(NebulaBlue, w * 0.82f + cos(t * 0.6f) * 26f, h * 0.18f + sin(t * 0.8f) * 20f, w * 0.50f, 0.28f)
    nebula(NebulaCyan, w * 0.55f + sin(t * 0.5f) * 30f, h * 0.85f + cos(t * 0.9f) * 22f, w * 0.60f, 0.22f)
}

private fun DrawScope.nebula(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Screen,
    )
}

private fun DrawScope.drawGodRays(time: Float) {
    val w = size.width
    val h = size.height
    val source = Offset(w * 0.5f, -h * 0.06f)
    val length = h * 1.35f
    val rayCount = 14
    val sweepDeg = 110f
    for (i in 0 until rayCount) {
        val frac = i / (rayCount - 1).toFloat()
        val deg = 35f + sweepDeg * frac
        val rad = deg * PI.toFloat() / 180f
        val sway = sin(time * 2f * PI.toFloat() * 0.5f + i) * 2f * PI.toFloat() / 180f
        val alpha = 0.055f + 0.025f * (0.5f + 0.5f * sin(i * 1.7f))
        drawGodRay(source, rad + sway, length, h * 0.016f, StarBlue, alpha)
    }
}

private fun DrawScope.drawGodRay(
    center: Offset,
    angleRad: Float,
    length: Float,
    halfWidth: Float,
    color: Color,
    alpha: Float,
) {
    val dir = Offset(cos(angleRad), sin(angleRad))
    val far = Offset(center.x + dir.x * length, center.y + dir.y * length)
    val perp = Offset(-sin(angleRad), cos(angleRad))
    val nearW = halfWidth * 0.25f
    val farW = halfWidth * 5f
    val p1 = center + perp * nearW
    val p2 = center - perp * nearW
    val p3 = far - perp * farW
    val p4 = far + perp * farW
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }
    val brush = Brush.linearGradient(
        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
        start = center,
        end = far,
    )
    drawPath(path, brush, blendMode = BlendMode.Screen)
}

private fun DrawScope.drawStars(stars: List<Star>, time: Float) {
    val w = size.width
    val h = size.height
    stars.forEach { s ->
        val twinkle = 0.55f + 0.45f * sin(2f * PI.toFloat() * (time * s.speed + s.phase))
        val color = if (s.warm) StarGold else StarWhite
        drawCircle(
            color = color.copy(alpha = s.baseAlpha * twinkle),
            radius = s.radius,
            center = Offset(s.x * w, s.y * h),
            blendMode = BlendMode.Screen,
        )
    }
}

private fun DrawScope.drawShootingStar(time: Float) {
    val w = size.width
    val h = size.height
    val period = 0.16f
    val cycle = (time / period) % 1f
    val fade = when {
        cycle < 0.08f -> cycle / 0.08f
        cycle > 0.85f -> (1f - cycle) / 0.15f
        else -> 1f
    }
    if (fade <= 0f) return
    val head = Offset(w * (0.95f - cycle * 0.7f), h * (0.05f + cycle * 0.35f))
    val tail = Offset(head.x + w * 0.16f, head.y - h * 0.08f)
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(StarWhite.copy(alpha = 0.9f * fade), Color.Transparent),
            start = head,
            end = tail,
        ),
        start = head,
        end = tail,
        strokeWidth = 2.2f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Screen,
    )
}

/** 北斗七星（单位坐标 0..1，取自图标同款排布）。 */
private val DipperUnit = listOf(
    Offset(0.179f, 0.300f), // 天枢
    Offset(0.179f, 0.679f), // 天璇
    Offset(0.450f, 0.779f), // 天玑
    Offset(0.520f, 0.400f), // 天权
    Offset(0.700f, 0.300f), // 玉衡
    Offset(0.860f, 0.340f), // 开阳
    Offset(0.980f, 0.550f), // 瑶光
)

private fun DrawScope.drawDipper(time: Float) {
    val left = size.width * 0.56f
    val top = size.height * 0.045f
    val boxW = size.width * 0.42f
    val boxH = size.height * 0.24f
    val pts = DipperUnit.map { Offset(left + it.x * boxW, top + it.y * boxH) }
    val lineColor = StarBlue.copy(alpha = 0.22f)

    val bowl = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        lineTo(pts[1].x, pts[1].y)
        lineTo(pts[2].x, pts[2].y)
        lineTo(pts[3].x, pts[3].y)
        close()
    }
    drawPath(bowl, lineColor, style = Stroke(width = 1.2f))

    val handle = Path().apply {
        moveTo(pts[3].x, pts[3].y)
        lineTo(pts[4].x, pts[4].y)
        lineTo(pts[5].x, pts[5].y)
        lineTo(pts[6].x, pts[6].y)
    }
    drawPath(handle, lineColor, style = Stroke(width = 1.2f))

    pts.forEachIndexed { i, p ->
        val tw = 0.5f + 0.5f * sin(2f * PI.toFloat() * (time * 1.5f + i * 0.13f))
        drawCircle(
            color = StarWhite.copy(alpha = 0.45f + 0.55f * tw),
            radius = 3.2f,
            center = p,
            blendMode = BlendMode.Screen,
        )
    }
}
