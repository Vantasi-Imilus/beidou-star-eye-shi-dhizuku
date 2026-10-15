package org.beidou.stareye.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

// 全部色值自 LocalBeidouPalette 读取：反向星界模式下自动反色。
// （保持顶层名字不变，既有使用点无需改动；使用处须在 @Composable 上下文）

// —— 深空背景 ——
val DeepSpace: Color @Composable get() = LocalBeidouPalette.current.deepSpace
val DeepSpaceDeep: Color @Composable get() = LocalBeidouPalette.current.deepSpaceDeep
val DeepSpaceLight: Color @Composable get() = LocalBeidouPalette.current.deepSpaceLight

// —— 星云 ——
val NebulaPurple: Color @Composable get() = LocalBeidouPalette.current.nebulaPurple
val NebulaBlue: Color @Composable get() = LocalBeidouPalette.current.nebulaBlue
val NebulaCyan: Color @Composable get() = LocalBeidouPalette.current.nebulaCyan

// —— 星光 ——
val StarWhite: Color @Composable get() = LocalBeidouPalette.current.starWhite
val StarBlue: Color @Composable get() = LocalBeidouPalette.current.starBlue
val StarGold: Color @Composable get() = LocalBeidouPalette.current.starGold

// —— 液态玻璃 ——
val GlassTint: Color @Composable get() = LocalBeidouPalette.current.glassTint
val GlassStroke: Color @Composable get() = LocalBeidouPalette.current.glassStroke

/** 玻璃高光基准色（正常为白 / 反向为黑） */
val GlassHighlight: Color @Composable get() = LocalBeidouPalette.current.glassHighlight

/**
 * 北斗七星七颗星的专属强调色（按斗序 1..7）。
 */
val StarAccents: List<Color> @Composable get() = LocalBeidouPalette.current.accents

/** 按斗序取星位强调色。 */
@Composable
fun starAccent(order: Int): Color {
    val accents = LocalBeidouPalette.current.accents
    return accents[(order - 1).coerceIn(0, accents.size - 1)]
}

/**
 * 统一圆角 —— 反向星界的锐利模式下全部收为直角。
 *
 * 所有界面原本的 `RoundedCornerShape(n.dp)` 均改用本函数，
 * 使「尖锐边框」与「圆润边框」随模式全局切换。
 */
@Composable
fun beidouShape(radius: Dp): Shape =
    if (LocalSharpCorners.current) RectangleShape else RoundedCornerShape(radius)

/** 百分比圆角（胶囊按钮等，如 50%） */
@Composable
fun beidouShape(percent: Int): Shape =
    if (LocalSharpCorners.current) RectangleShape else RoundedCornerShape(percent)
