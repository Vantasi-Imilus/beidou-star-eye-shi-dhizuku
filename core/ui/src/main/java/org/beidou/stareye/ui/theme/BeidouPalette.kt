package org.beidou.stareye.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 反向星界取反：RGB 逐通道取反、alpha 保留。
 *
 * 反色后 深空底 → 暖白底、星光字 → 深墨字，整体呈「亮底暗字」的反差观感。
 */
private fun Color.inverted(): Color =
    Color(red = 1f - red, green = 1f - green, blue = 1f - blue, alpha = alpha)

/**
 * 北斗主题调色板 —— 全部语义色 token 的唯一取值源。
 *
 * `Color.kt` 中的顶层色值（DeepSpace / StarWhite / …）均从 [LocalBeidouPalette] 读取，
 * 反向星界模式下整个调色板切换为 [ReversedPalette]，所有界面即刻反色。
 */
data class BeidouPalette(
    val deepSpace: Color,
    /** 更深的深空（背景渐变的中间色） */
    val deepSpaceDeep: Color,
    val deepSpaceLight: Color,
    val nebulaPurple: Color,
    val nebulaBlue: Color,
    val nebulaCyan: Color,
    val starWhite: Color,
    val starBlue: Color,
    val starGold: Color,
    val glassTint: Color,
    val glassStroke: Color,
    val accents: List<Color>,
    /** 玻璃高光基准（正常为白、反向为黑） */
    val glassHighlight: Color,
    /** 玻璃描边渐变的基准色 */
    val glassEdge: Color,
) {
    /** 反向星界：逐通道取反 */
    fun inverted(): BeidouPalette = BeidouPalette(
        deepSpace = deepSpace.inverted(),
        deepSpaceDeep = deepSpaceDeep.inverted(),
        deepSpaceLight = deepSpaceLight.inverted(),
        nebulaPurple = nebulaPurple.inverted(),
        nebulaBlue = nebulaBlue.inverted(),
        nebulaCyan = nebulaCyan.inverted(),
        starWhite = starWhite.inverted(),
        starBlue = starBlue.inverted(),
        starGold = starGold.inverted(),
        glassTint = glassTint.inverted(),
        glassStroke = glassStroke.inverted(),
        accents = accents.map { it.inverted() },
        glassHighlight = glassHighlight.inverted(),
        glassEdge = glassEdge.inverted(),
    )
}

/** 正常（主界）调色板 */
val NormalPalette = BeidouPalette(
    deepSpace = Color(0xFF05070F),
    deepSpaceDeep = Color(0xFF020308),
    deepSpaceLight = Color(0xFF0A0F24),
    nebulaPurple = Color(0xFF3A2A6B),
    nebulaBlue = Color(0xFF1B3A6B),
    nebulaCyan = Color(0xFF2E6B8A),
    starWhite = Color(0xFFEAF6FF),
    starBlue = Color(0xFF8FD8FF),
    starGold = Color(0xFFFFD9A0),
    glassTint = Color(0x12FFFFFF),
    glassStroke = Color(0x38FFFFFF),
    accents = listOf(
        Color(0xFF8FD8FF), // 天枢 蓝
        Color(0xFF7CE0C3), // 天璇 青绿
        Color(0xFFA8A0FF), // 天玑 紫
        Color(0xFFFFC98A), // 天权 金
        Color(0xFF9BD9FF), // 玉衡 冰蓝
        Color(0xFFFF9FC0), // 开阳 粉
        Color(0xFF8FA6FF), // 瑶光 靛
    ),
    glassHighlight = Color.White,
    glassEdge = Color.White,
)

/** 反向星界调色板（对 [NormalPalette] 逐通道取反） */
val ReversedPalette: BeidouPalette = NormalPalette.inverted()

/** 当前生效的调色板 */
val LocalBeidouPalette = staticCompositionLocalOf { NormalPalette }

/** 反向星界模式：边框圆角全部收为直角（锐利感） */
val LocalSharpCorners = staticCompositionLocalOf { false }
