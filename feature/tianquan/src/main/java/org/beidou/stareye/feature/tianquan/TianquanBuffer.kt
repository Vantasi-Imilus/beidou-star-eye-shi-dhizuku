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
 * 终端字符显示宽度（模拟器写入与重排共用）：
 * 0 = 不可打印（组合符号等），1 = 半角，2 = 全角（宽字符占两格）。
 */
internal fun terminalCharWidth(code: Int): Int = when {
    code == 0 -> 0
    code < 0x20 -> 0
    code == 0x200B -> 0
    code in 0x0300..0x036F -> 0            // 组合符号
    code in 0x1100..0x115F -> 2
    code in 0x2E80..0x303E -> 2
    code in 0x3041..0x33FF -> 2
    code in 0x3400..0x4DBF -> 2
    code in 0x4E00..0x9FFF -> 2
    code in 0xA000..0xA4CF -> 2
    code in 0xAC00..0xD7A3 -> 2
    code in 0xF900..0xFAFF -> 2
    code in 0xFE30..0xFE6F -> 2
    code in 0xFF00..0xFF60 -> 2
    code in 0xFFE0..0xFFE6 -> 2
    code >= 0x20000 -> 2
    else -> 1
}

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
        // 整行清除后不再是上一行的软换行续行（重排按此标志合并逻辑行）
        if (start == 0 && end == cols) wrapped = false
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

    /**
     * 宽度变化重排（reflow）：仅当列数变化时调用；行数变化仍走 [resize]。
     *
     * 思路（参考 Termux TerminalBuffer 的 reflow 设计，完全自研实现）：
     *  1. 把「回滚历史 + 屏幕」按行序拼成连续内容；
     *  2. 以每行的 [TerminalRow.wrapped] 标志切成「逻辑行」——wrapped=true 的行是
     *     上一行的软换行续行，wrapped=false 处才是真正的硬换行；
     *  3. 每条逻辑行按新宽度重新切分：中间切出的行标记 wrapped=true，
     *     末尾行保留原逻辑行的 wrapped 标志；
     *  4. 重排后行数可能增减：屏幕取最后 [rows] 行（不足则顶部补空行，底部锚定），
     *     其余按容量放入回滚历史。
     *
     * 保守策略：
     *  · 宽字符（宽度 2）与其占位哨兵作为一个整体搬移，绝不拆散；
     *    若新行只剩 1 格，宽字符整体换到下一行（与 putChar 的自动换行语义一致）；
     *  · 行尾空格原样保留（不清尾，避免破坏光标位置假设）；
     *  · 备用屏由调用方决定不重排（本方法只被主屏调用）。
     */
    fun reflowResize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == cols) return
        if (newCols == cols) {
            resize(newRows, newCols)
            return
        }

        // 1. 汇总旧内容（回滚 + 屏幕，均为旧宽度）
        val combined = ArrayList<TerminalRow>(scrollback.size + rows)
        scrollback.forEach { combined.add(it) }
        screen.forEach { combined.add(it) }

        // 2. 切成逻辑行：wrapped=true 的行并入上一条逻辑行
        val logicalLines = ArrayList<LogicalLine>()
        var current = LogicalLine()
        for (row in combined) {
            current.append(row)
            if (!row.wrapped) {
                logicalLines.add(current)
                current = LogicalLine()
            }
        }
        if (current.size > 0) logicalLines.add(current)   // 末尾仍处于软换行续行状态

        // 3. 按新宽度重新软换行
        val reflowed = ArrayList<TerminalRow>()
        for (line in logicalLines) line.rewrapInto(reflowed, newCols)

        // 4. 分配屏幕与回滚：屏幕底部锚定，多余行进入回滚（按容量截断）
        scrollback.clear()
        if (reflowed.size >= newRows) {
            val extra = reflowed.size - newRows
            if (extra > 0) {
                val keep = minOf(extra, maxScrollback)
                for (i in extra - keep until extra) scrollback.addLast(reflowed[i])
            }
            screen = Array(newRows) { reflowed[extra + it] }
        } else {
            val pad = newRows - reflowed.size
            screen = Array(newRows) { y ->
                if (y >= pad) reflowed[y - pad] else TerminalRow(newCols)
            }
        }
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

/**
 * 重排用的「逻辑行」：由若干物理行（wrapped 续行）拼成的一串单元格。
 */
private class LogicalLine {
    val chars = ArrayList<Char>()
    val styles = ArrayList<Long>()

    /** 逻辑行末尾是否仍处于软换行续行状态（取自其最后一条物理行的 wrapped 标志） */
    var wrapped = false

    val size: Int get() = chars.size

    fun append(row: TerminalRow) {
        for (i in 0 until row.cols) {
            chars.add(row.text[i])
            styles.add(row.styles[i])
        }
        wrapped = row.wrapped
    }

    /**
     * 按新宽度重新软换行并追加到 [out]。
     * 中间切出的行 wrapped=true；最后一行保留本逻辑行的 wrapped 标志。
     */
    fun rewrapInto(out: MutableList<TerminalRow>, width: Int) {
        var row = TerminalRow(width)
        var used = 0
        var i = 0
        while (i < chars.size) {
            val ch = chars[i]
            val st = styles[i]
            val w = if (ch == WIDE_PLACEHOLDER) 1 else terminalCharWidth(ch.code).coerceAtLeast(1)
            if (w == 2) {
                // 宽字符与其后的占位哨兵整体搬移：剩余不足 2 格则整行换到下一行
                if (used + 2 > width) {
                    row.wrapped = true
                    out.add(row)
                    row = TerminalRow(width)
                    used = 0
                }
                row.text[used] = ch
                row.styles[used] = st
                if (used + 1 < width) {
                    row.text[used + 1] = WIDE_PLACEHOLDER
                    row.styles[used + 1] = st
                }
                used += 2
                i += if (i + 1 < chars.size && chars[i + 1] == WIDE_PLACEHOLDER) 2 else 1
                continue
            }
            if (used >= width) {
                row.wrapped = true
                out.add(row)
                row = TerminalRow(width)
                used = 0
            }
            row.text[used] = ch
            row.styles[used] = st
            used++
            i++
        }
        row.wrapped = wrapped
        out.add(row)
    }
}
