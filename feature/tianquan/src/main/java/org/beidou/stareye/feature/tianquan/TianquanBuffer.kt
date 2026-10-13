package org.beidou.stareye.feature.tianquan

/**
 * 天权中枢 —— 终端屏幕缓冲（自研实现，零拷贝）。
 *
 * 每个单元格 = 字符 + 打包样式（前景/背景色 + 粗体/暗显/斜体/下划线/闪烁/反显/隐藏/删除线）。
 * 样式打包进一个 Long：足以容纳 24 位真彩与全部常用属性，且无需为每格分配对象。
 */

/** 颜色索引：0..255 为调色板，[DEFAULT_COLOR] 表示终端默认前景/背景 */
const val DEFAULT_COLOR = 256

/** 用于「宽字符右侧占位格」的哨兵字符，渲染时跳过 */
const val WIDE_PLACEHOLDER = '\u0000'

/**
 * 打包样式布局（Long）：
 * ```
 *  0..23  前景值（索引或 24 位 RGB）
 *  24     前景为真彩
 *  25..48 背景值
 *  49     背景为真彩
 *  50+    属性标志位
 * ```
 */
object Style {

    private const val BG_SHIFT = 25

    /**
     * 默认样式：前景/背景均为 [DEFAULT_COLOR]（即"终端默认色"，**不是** ANSI 色号 0）。
     *
     * 注意：不能写成 0L —— 那会被解码成 ANSI 色号 0（通常是很暗的颜色），
     * 导致默认色文字与背景同色而"隐形"。
     */
    val DEFAULT: Long = DEFAULT_COLOR.toLong() or (DEFAULT_COLOR.toLong() shl BG_SHIFT)

    private const val FG_MASK = 0x1FFFFFFL      // 25 位：24 位值 + 真彩标志
    private const val BG_MASK = 0x1FFFFFFL shl BG_SHIFT

    const val FLAG_BOLD = 1L shl 50
    const val FLAG_DIM = 1L shl 51
    const val FLAG_ITALIC = 1L shl 52
    const val FLAG_UNDERLINE = 1L shl 53
    const val FLAG_BLINK = 1L shl 54
    const val FLAG_REVERSE = 1L shl 55
    const val FLAG_INVISIBLE = 1L shl 56
    const val FLAG_STRIKE = 1L shl 57

    /** 前景：返回颜色索引；若为真彩则返回 [DEFAULT_COLOR] 以外的哨兵由 [fgRgb] 取 */
    fun fgIndex(style: Long): Int = (style and FG_MASK).toInt() and 0xFFFF

    /** 前景是否为真彩 */
    fun fgIsRgb(style: Long): Boolean = (style and FG_MASK) and (1L shl 24) != 0L

    fun fgRgb(style: Long): Int = (style and 0xFFFFFFL).toInt()

    fun bgIndex(style: Long): Int = ((style and BG_MASK) ushr BG_SHIFT).toInt() and 0xFFFF

    fun bgIsRgb(style: Long): Boolean = (style and BG_MASK) and (1L shl 49) != 0L

    fun bgRgb(style: Long): Int = ((style and (0xFFFFFFL shl BG_SHIFT)) ushr BG_SHIFT).toInt()

    fun withFg(index: Int): Long = index.toLong() and 0xFFFFL
    fun withFgRgb(rgb: Int): Long = (rgb.toLong() and 0xFFFFFFL) or (1L shl 24)
    fun withBg(style: Long, index: Int): Long =
        (style and FG_MASK) or ((index.toLong() and 0xFFFFL) shl BG_SHIFT)

    fun withBgRgb(style: Long, rgb: Int): Long =
        (style and FG_MASK) or (((rgb.toLong() and 0xFFFFFFL) or (1L shl 24)) shl BG_SHIFT)

    fun withFlag(style: Long, flag: Long, on: Boolean): Long =
        if (on) style or flag else style and flag.inv()

    fun has(style: Long, flag: Long): Boolean = style and flag != 0L
}

/** 一行终端内容 */
class TerminalRow(val cols: Int) {
    val text = CharArray(cols) { ' ' }
    val styles = LongArray(cols) { Style.DEFAULT }

    /** 该行是否因自动换行而结束 */
    var wrapped = false

    fun clear(from: Int = 0, to: Int = cols, style: Long = Style.DEFAULT) {
        val start = from.coerceAtLeast(0)
        val end = minOf(to, cols)
        for (i in start until end) {
            text[i] = ' '
            styles[i] = style
        }
    }

    fun copyFrom(other: TerminalRow) {
        val n = minOf(cols, other.cols)
        System.arraycopy(other.text, 0, text, 0, n)
        System.arraycopy(other.styles, 0, styles, 0, n)
        if (n < cols) clear(n, cols)
        wrapped = other.wrapped
    }

    /** 行尾有效长度（去掉尾部空白且为默认样式者），便于渲染 */
    fun trimmedLength(): Int {
        var end = cols
        while (end > 0 && text[end - 1] == ' ' && styles[end - 1] == Style.DEFAULT) end--
        return end
    }

    fun plainText(from: Int, to: Int): String {
        val sb = StringBuilder(to - from)
        for (i in from until minOf(to, cols)) {
            val c = text[i]
            if (c != WIDE_PLACEHOLDER) sb.append(c)
        }
        return sb.toString()
    }
}

/**
 * 一块屏幕（可见区 [rows] 行 + 回滚历史）。
 * 主屏带历史，备用屏不带（[maxScrollback] = 0）。
 */
class TerminalBuffer(
    var rows: Int,
    var cols: Int,
    private val maxScrollback: Int = 2000,
) {
    var screen: Array<TerminalRow> = Array(rows) { TerminalRow(cols) }
    val scrollback = ArrayDeque<TerminalRow>()

    val hasScrollback: Boolean get() = maxScrollback > 0

    fun resize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == cols) return
        val resized = Array(newRows) { y ->
            val row = TerminalRow(newCols)
            screen.getOrNull(y)?.let { row.copyFrom(it) }
            row
        }
        screen = resized
        rows = newRows
        cols = newCols
    }

    /** 上滚一行：顶部行进入回滚历史（仅主屏保留） */
    fun scrollUp(top: Int = 0, bottom: Int = rows - 1) {
        if (top > bottom) return
        if (top == 0 && hasScrollback) {
            val saved = TerminalRow(cols).also { it.copyFrom(screen[0]) }
            scrollback.addLast(saved)
            while (scrollback.size > maxScrollback) scrollback.removeFirst()
        }
        for (y in top until bottom) screen[y].copyFrom(screen[y + 1])
        screen[bottom].clear()
    }

    /** 下滚一行（底部行丢弃） */
    fun scrollDown(top: Int = 0, bottom: Int = rows - 1) {
        for (y in bottom downTo top + 1) screen[y].copyFrom(screen[y - 1])
        screen[top].clear()
    }

    fun clearAll() = screen.forEach { it.clear() }

    fun clearScrollback() = scrollback.clear()
}
