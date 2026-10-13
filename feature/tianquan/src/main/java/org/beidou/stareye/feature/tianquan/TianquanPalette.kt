package org.beidou.stareye.feature.tianquan

import androidx.compose.ui.graphics.Color

/**
 * 天权中枢 —— 终端配色方案与**运行时调色板**。
 *
 * 主题提供 16 色 ANSI 调色板；运行时可通过 OSC 4（改调色板项）、
 * OSC 10/11/12（改默认前景/背景/光标）动态覆盖，且可被 OSC 104/110/111/112 复位。
 */
data class TerminalTheme(
    val id: String,
    val label: String,
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    /** 前 8 个为普通色，后 8 个为高亮色 */
    val ansi: List<Color>,
)

/** 内置配色（含按北斗星空风格调制的方案） */
object TerminalThemes {

    private val starry = TerminalTheme(
        id = "starry",
        label = "星空（默认）",
        background = Color(0xFF05070F),
        foreground = Color(0xFFEAF6FF),
        cursor = Color(0xFF8FD8FF),
        ansi = listOf(
            Color(0xFF1B2430), Color(0xFFFF6B81), Color(0xFF7CE0C3), Color(0xFFFFD9A0),
            Color(0xFF8FD8FF), Color(0xFFB9A8FF), Color(0xFF7FE3F0), Color(0xFFD5E3F0),
            Color(0xFF4A5A70), Color(0xFFFF8FA3), Color(0xFF9BF0D8), Color(0xFFFFE7B8),
            Color(0xFFAEE6FF), Color(0xFFCFC2FF), Color(0xFFA5F0FA), Color(0xFFFFFFFF),
        ),
    )

    private val classic = TerminalTheme(
        id = "classic",
        label = "经典",
        background = Color(0xFF000000),
        foreground = Color(0xFFD0D0D0),
        cursor = Color(0xFFD0D0D0),
        ansi = listOf(
            Color(0xFF000000), Color(0xFFCD0000), Color(0xFF00CD00), Color(0xFFCDCD00),
            Color(0xFF0000EE), Color(0xFFCD00CD), Color(0xFF00CDCD), Color(0xFFE5E5E5),
            Color(0xFF7F7F7F), Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFFFFFF00),
            Color(0xFF5C5CFF), Color(0xFFFF00FF), Color(0xFF00FFFF), Color(0xFFFFFFFF),
        ),
    )

    private val paper = TerminalTheme(
        id = "paper",
        label = "浅色",
        background = Color(0xFFF7F7F2),
        foreground = Color(0xFF22262B),
        cursor = Color(0xFF2B6CB0),
        ansi = listOf(
            Color(0xFF22262B), Color(0xFFB03030), Color(0xFF2E7D32), Color(0xFF9A7B00),
            Color(0xFF2B6CB0), Color(0xFF7B3FA0), Color(0xFF0F7B8A), Color(0xFFBFBFBF),
            Color(0xFF5A626B), Color(0xFFD14545), Color(0xFF43A047), Color(0xFFB8912A),
            Color(0xFF3E86C9), Color(0xFF9457BF), Color(0xFF1E9AA8), Color(0xFF101214),
        ),
    )

    private val solar = TerminalTheme(
        id = "solar",
        label = "暗金",
        background = Color(0xFF0B0D12),
        foreground = Color(0xFFF2E9D8),
        cursor = Color(0xFFFFC98A),
        ansi = listOf(
            Color(0xFF2B2A26), Color(0xFFE06C5A), Color(0xFF9BC46A), Color(0xFFE8B75A),
            Color(0xFF7FA8D8), Color(0xFFC08CD8), Color(0xFF6FC0B8), Color(0xFFD8D2C4),
            Color(0xFF5A564C), Color(0xFFFF8A76), Color(0xFFB6DC86), Color(0xFFFFD07A),
            Color(0xFF9CC0E8), Color(0xFFD8A8F0), Color(0xFF8ED8D0), Color(0xFFFFFFFF),
        ),
    )

    val all = listOf(starry, classic, paper, solar)

    fun byId(id: String?): TerminalTheme = all.firstOrNull { it.id == id } ?: starry
}

private val CUBE_STEPS = intArrayOf(0, 95, 135, 175, 215, 255)

/**
 * 运行时调色板：主题为底，叠加 OSC 动态覆盖。
 */
class TerminalPalette(var theme: TerminalTheme) {

    /** OSC 4：调色板项覆盖（index → 0xRRGGBB） */
    private val overrides = HashMap<Int, Int>(8)
    private var fgOverride: Int? = null
    private var bgOverride: Int? = null
    private var cursorOverride: Int? = null

    fun resetAll() {
        overrides.clear()
        fgOverride = null
        bgOverride = null
        cursorOverride = null
    }

    fun setPalette(index: Int, rgb: Int) {
        if (index in 0..255) overrides[index] = rgb
    }

    fun getPalette(index: Int): Int? = overrides[index]

    fun resetPalette(index: Int) {
        overrides.remove(index)
    }

    fun setDefaultForeground(rgb: Int) { fgOverride = rgb }
    fun setDefaultBackground(rgb: Int) { bgOverride = rgb }
    fun setCursorColor(rgb: Int) { cursorOverride = rgb }

    fun resetForeground() { fgOverride = null }
    fun resetBackground() { bgOverride = null }
    fun resetCursor() { cursorOverride = null }

    /** 查询当前生效的调色板项（供 OSC 4 的 `?` 查询应答） */
    fun currentPaletteRgb(index: Int): Int {
        overrides[index]?.let { return it }
        val c = baseColor(index, true)
        return (c.red.toInt() shl 16) or (c.green.toInt() shl 8) or c.blue.toInt()
    }

    fun foreground(): Color = fgOverride?.let(::colorOf) ?: theme.foreground

    fun background(): Color = bgOverride?.let(::colorOf) ?: theme.background

    fun cursor(): Color = cursorOverride?.let(::colorOf) ?: theme.cursor

    /** 解析单元格颜色（[isForeground] 用于默认色回退） */
    fun resolve(index: Int, isForeground: Boolean): Color = when {
        index == DEFAULT_COLOR -> if (isForeground) foreground() else background()
        else -> overrides[index]?.let(::colorOf) ?: baseColor(index, isForeground)
    }

    private fun baseColor(index: Int, isForeground: Boolean): Color = when {
        index in 0..15 -> theme.ansi[index]
        index in 16..231 -> {
            val i = index - 16
            val r = CUBE_STEPS[i / 36]
            val g = CUBE_STEPS[(i % 36) / 6]
            val b = CUBE_STEPS[i % 6]
            rgbColor(r, g, b)
        }
        index in 232..255 -> {
            val level = 8 + (index - 232) * 10
            rgbColor(level, level, level)
        }
        else -> if (isForeground) foreground() else background()
    }

    private fun colorOf(rgb: Int): Color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

    private fun rgbColor(r: Int, g: Int, b: Int): Color =
        Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
}

/** 24 位 RGB → 调色板近似（用于不支持真彩时的降级） */
fun rgbToAnsi256(r: Int, g: Int, b: Int): Int {
    if (r == g && g == b) {
        return if (r < 8) 16 else if (r > 248) 231 else ((r - 8) / 247.0 * 24).toInt() + 232
    }
    val ri = (r / 255.0 * 5).toInt().coerceIn(0, 5)
    val gi = (g / 255.0 * 5).toInt().coerceIn(0, 5)
    val bi = (b / 255.0 * 5).toInt().coerceIn(0, 5)
    return 16 + 36 * ri + 6 * gi + bi
}
