package org.beidou.stareye.feature.tianquan

import android.util.Base64
import java.io.ByteArrayOutputStream

/** 鼠标上报模式（DECSET 1000/1002/1003） */
enum class MouseMode { NONE, CLICK, DRAG, ANY }

/** 鼠标上报编码协议（DECSET 1005/1006/1015/1016；开启其一互斥关闭其它，与 xterm 一致） */
enum class MouseEncoding { X10, UTF8, URXVT, SGR, SGR_PIXEL }

/** 供 UI 使用的鼠标按钮编码 */
object MouseButton {
    const val LEFT = 0
    const val MIDDLE = 1
    const val RIGHT = 2
    const val WHEEL_UP = 64
    const val WHEEL_DOWN = 65
    const val MOD_SHIFT = 4
    const val MOD_ALT = 8
    const val MOD_CTRL = 16
}

/**
 * 天权中枢 —— 终端模拟器（自研实现，零拷贝）。
 *
 * 覆盖：
 *  · C0 控制符、CSI（光标/擦除/插删/滚动/滚动区域/REP/制表位）、SGR（含 24 位真彩与全部常用属性）
 *  · ESC 类：RIS / IND / RI / NEL / DECSC / DECRC / DECALN / 字符集（G0-G3 指定、
 *    SO/SI、LS2/LS3、SS2/SS3、LS1R/LS2R/LS3R、DEC 线绘 + 英/德/法/丹挪/瑞典/意/西/荷/芬
 *    国家字符集 + Latin-1 兜底）
 *  · 备用屏（47/1047/1048/1049）、原点模式、自动换行、应用光标键、反显、光标显隐与样式（DECSCUSR）
 *  · 鼠标上报：X10 / UTF-8 1005 / urxvt 1015 / SGR 1006 / SGR 像素 1016（互斥切换）
 *  · 宽度变化重排（reflow：主屏 + 回滚历史；DECCOLM 132 列切换自动联动）
 *  · 括号粘贴（2004）、焦点事件占位（1004）
 *  · OSC：0/1/2 标题、4 调色板、10/11/12 默认前景/背景/光标、52 剪贴板、104/110/111/112 复位
 *  · 应答：DA1/DA2、DSR（5n / 6n / ?6n）、XTGETTCAP 忽略
 *  · kitty 图形协议（APC `ESC _ G`）与 iTerm2 内联图像（OSC 1337）：**完整消费**序列，
 *    保证 base64 载荷绝不落入屏幕文本；图像渲染属渲染层改动，本次有意不做
 *
 * 未覆盖（有意取舍）：内联图像的渲染；窗口操作（CSI t）忽略。
 */
class TianquanEmulator(
    rows: Int,
    cols: Int,
    val palette: TerminalPalette = TerminalPalette(TerminalThemes.all.first()),
    private val scrollbackRows: Int = 2000,
    /** 需要回写给 shell 的应答字节 */
    private val onResponse: (ByteArray) -> Unit = {},
    /** OSC 0/1/2 标题变化 */
    private val onTitle: (String) -> Unit = {},
    /** OSC 52 写剪贴板 */
    private val onClipboard: (String) -> Unit = {},
    /** BEL */
    private val onBell: () -> Unit = {},
    /** 请求改变终端尺寸（如 DECCOLM 132 列） */
    private val onResizeRequest: (Int, Int) -> Unit = { _, _ -> },
) {

    private val primary = TerminalBuffer(rows, cols, scrollbackRows)
    private val alternate = TerminalBuffer(rows, cols, 0)
    private var active: TerminalBuffer = primary

    /** 当前可见缓冲（渲染用） */
    val buffer: TerminalBuffer get() = active

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true
        private set

    /** 光标样式（0 跟随设置；1/2 方块，3/4 下划线，5/6 竖条；偶数为不闪） */
    var cursorStyleCode = 0
        private set

    var title: String = ""
        private set

    var mouseMode: MouseMode = MouseMode.NONE
        private set

    /** 当前鼠标上报编码协议 */
    var mouseEncoding = MouseEncoding.X10
        private set

    /** 兼容旧接口：是否 SGR 系编码（1006 / 1016） */
    val mouseSgrEncoding: Boolean
        get() = mouseEncoding == MouseEncoding.SGR || mouseEncoding == MouseEncoding.SGR_PIXEL

    var bracketedPaste = false
        private set

    /** 屏幕内容版本号，用于触发 UI 重绘 */
    var revision: Long = 0L
        private set

    // ── 样式状态 ──
    private var fgIsRgb = false
    private var fgValue = DEFAULT_COLOR          // 索引或 RGB
    private var bgIsRgb = false
    private var bgValue = DEFAULT_COLOR
    private var attrFlags = 0L
    private var currentStyle = Style.DEFAULT

    // ── 模式 ──
    private var autoWrap = true
    private var originMode = false
    private var insertMode = false
    private var appCursorKeys = false
    private var reverseVideo = false
    private var trueColorEnabled = true

    // ── 滚动区域 ──
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    // ── 制表位 ──
    private var tabStops = BooleanArray(cols) { it % 8 == 0 }

    // ── 字符集（DEC NRCS）──
    // G0..G3 四个可指定字符集；GL 区（0x00-0x7F）经 glSlot 选 G0..G3，
    // GR 区（0x80-0xFF）经 grSlot 选 G1..G3；ssPending 为 SS2/SS3 单字符移位。
    private var g0Charset = 'B'
    private var g1Charset = 'B'
    private var g2Charset = 'B'
    private var g3Charset = 'B'
    private var glSlot = 0
    private var grSlot = 1
    private var ssPending = 0

    // ── 保存的光标（主屏/备用屏各一份） ──
    private class SavedCursor {
        var row = 0
        var col = 0
        var fg = DEFAULT_COLOR
        var bg = DEFAULT_COLOR
        var fgRgb = false
        var bgRgb = false
        var flags = 0L
        var g0 = 'B'
        var g1 = 'B'
        var g2 = 'B'
        var g3 = 'B'
        var gl = 0
        var gr = 1
        var origin = false
        var autoWrapMode = true
    }

    private val savedPrimary = SavedCursor()
    private val savedAlternate = SavedCursor()
    private fun saved() = if (active === primary) savedPrimary else savedAlternate

    private var lastPrintedChar = ' '

    // ── 解析状态 ──
    // APC：kitty 图形协议（ESC _ G ... ESC \），内容只消费不落屏；
    // ESC_PERCENT：ESC % G/@（UTF-8 指定）的中间态，避免 'G' 泄漏成文本。
    private enum class State { GROUND, ESC, ESC_CHARSET, ESC_PERCENT, CSI, OSC, OSC_ESC, DCS, DCS_ESC, APC, APC_ESC }
    private var state = State.GROUND
    private val params = StringBuilder()
    private val intermediates = StringBuilder()
    private var privateMarker = ' '
    private val oscBuf = StringBuilder()

    private val carry = ByteArrayOutputStream()

    // ───────────────────────── 对外操作 ─────────────────────────

    fun resize(newRows: Int, newCols: Int) {
        if (newRows == primary.rows && newCols == primary.cols) return
        // 主屏（含回滚历史）按新宽度重排软换行；备用屏保守处理：仅截断不重排
        primary.reflowResize(newRows, newCols)
        alternate.resize(newRows, newCols)
        if (tabStops.size != newCols) {
            val fresh = BooleanArray(newCols) { it % 8 == 0 }
            tabStops.copyInto(fresh, 0, 0, minOf(tabStops.size, newCols))
            tabStops = fresh
        }
        scrollTop = 0
        scrollBottom = newRows - 1
        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorCol = cursorCol.coerceIn(0, newCols - 1)
        bump()
    }

    fun feed(data: ByteArray, length: Int) {
        val text = decodeUtf8(data, length)
        if (text.isEmpty()) return
        for (ch in text) processChar(ch)
        bump()
    }

    /** 供 UI 控制是否直通 24 位色 */
    fun setTrueColor(enabled: Boolean) {
        trueColorEnabled = enabled
    }

    /** 括号粘贴：由 UI 在粘贴时调用 */
    fun paste(text: String) {
        val normalized = text.replace("\r\n", "\r").replace('\n', '\r')
        if (bracketedPaste) onResponse("\u001B[200~$normalized\u001B[201~".toByteArray(Charsets.UTF_8))
        else onResponse(normalized.toByteArray(Charsets.UTF_8))
    }

    /** 应用光标键模式下的方向键序列；UI 用它决定发送 ESC[A 还是 ESC OA */
    fun arrowKeySequence(final: Char): String =
        (if (appCursorKeys) "\u001B O" else "\u001B[") + final

    /**
     * 上报鼠标事件。坐标以 0 为基（列、行）。
     *
     * @param button 见 [MouseButton]（含修饰位）
     * @param press 按下为 true，抬起为 false
     * @param motion 是否为移动事件
     * @param pixelCol / pixelRow SGR 像素模式（1016）下的像素坐标（0 为基）；
     *        未提供时回退为单元格坐标
     */
    fun reportMouse(
        button: Int,
        col: Int,
        row: Int,
        press: Boolean,
        motion: Boolean,
        pixelCol: Int = -1,
        pixelRow: Int = -1,
    ) {
        if (mouseMode == MouseMode.NONE) return
        if (motion && mouseMode != MouseMode.ANY && mouseMode != MouseMode.DRAG) return

        val b = button or (if (motion) 32 else 0)
        when (mouseEncoding) {
            MouseEncoding.SGR, MouseEncoding.SGR_PIXEL -> {
                // 1006 报按下与抬起；1016 坐标改用像素
                val cx = if (mouseEncoding == MouseEncoding.SGR_PIXEL && pixelCol >= 0) pixelCol else col + 1
                val cy = if (mouseEncoding == MouseEncoding.SGR_PIXEL && pixelRow >= 0) pixelRow else row + 1
                val seq = "\u001B[<$b;$cx;$cy${if (press) 'M' else 'm'}"
                onResponse(seq.toByteArray(Charsets.UTF_8))
            }
            MouseEncoding.URXVT -> {
                // 1015（urxvt）：只上报按下与移动，抬起不发
                if (!press) return
                onResponse("\u001B[$b;${col + 1};${row + 1}M".toByteArray(Charsets.UTF_8))
            }
            MouseEncoding.UTF8 -> {
                // 1005：与 X10 同值，但每个字节按 UTF-8 码点编码
                if (!press && !motion && mouseMode == MouseMode.CLICK) return
                val payload = byteArrayOf(0x1B, '['.code.toByte(), 'M'.code.toByte()) +
                    utf8MouseByte(b + 32) + utf8MouseByte(col + 1 + 32) + utf8MouseByte(row + 1 + 32)
                onResponse(payload)
            }
            MouseEncoding.X10 -> {
                // 1000 模式只报按下，抬起不发
                if (!press && !motion && mouseMode == MouseMode.CLICK) return
                val cb = (b + 32).coerceIn(32, 255)
                val cx = (col + 1 + 32).coerceIn(32, 255)
                val cy = (row + 1 + 32).coerceIn(32, 255)
                onResponse(byteArrayOf(0x1B, '['.code.toByte(), 'M'.code.toByte(), cb.toByte(), cx.toByte(), cy.toByte()))
            }
        }
    }

    /** 把 0..255 的鼠标字节值编码成 UTF-8 码点（1005 协议：值为码点 U+00xx） */
    private fun utf8MouseByte(v0: Int): ByteArray {
        val v = v0.coerceIn(32, 255)
        return if (v < 0x80) {
            byteArrayOf(v.toByte())
        } else {
            byteArrayOf((0xC0 or (v shr 6)).toByte(), (0x80 or (v and 0x3F)).toByte())
        }
    }

    fun clearScrollback() {
        primary.clearScrollback()
        bump()
    }

    /** 软复位（DECSTR） */
    fun softReset() {
        applyModes(listOf(1, 1000, 1002, 1003, 1005, 1006, 1015, 1016, 2004), false)
        originMode = false
        autoWrap = true
        insertMode = false
        scrollTop = 0
        scrollBottom = active.rows - 1
        resetAttributes()
        cursorVisible = true
        bump()
    }

    private fun bump() {
        revision++
    }

    // ───────────────────────── UTF-8 增量解码 ─────────────────────────

    private fun decodeUtf8(data: ByteArray, length: Int): String {
        carry.write(data, 0, length)
        val all = carry.toByteArray()
        carry.reset()

        var end = all.size
        var cut = end
        var back = 0
        while (cut > 0 && back < 4) {
            val b = all[cut - 1].toInt() and 0xFF
            if (b and 0xC0 == 0x80) {
                cut--
                back++
            } else {
                val need = when {
                    b and 0x80 == 0 -> 1
                    b and 0xE0 == 0xC0 -> 2
                    b and 0xF0 == 0xE0 -> 3
                    b and 0xF8 == 0xF0 -> 4
                    else -> 1
                }
                if (end - (cut - 1) < need) cut--
                break
            }
        }
        if (cut < end && cut > 0) end = cut - 1

        val ready = if (end <= 0) "" else String(all, 0, end, Charsets.UTF_8)
        if (end in 0 until all.size) carry.write(all, end, all.size - end)
        return ready
    }

    // ───────────────────────── 主状态机 ─────────────────────────

    private fun processChar(ch: Char) {
        when (state) {
            State.GROUND -> when {
                ch == '\u001B' -> state = State.ESC
                ch == '\u0007' -> onBell()
                ch == '\b' -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
                ch == '\t' -> nextTabStop()
                ch == '\n' || ch == '\u000B' || ch == '\u000C' -> lineFeed()
                ch == '\r' -> cursorCol = 0
                ch == '\u000E' -> glSlot = 1         // SO：GL ← G1
                ch == '\u000F' -> glSlot = 0         // SI：GL ← G0
                ch < ' ' -> Unit
                else -> putChar(ch)
            }

            State.ESC -> {
                if (ch == '(' || ch == ')' || ch == '*' || ch == '+') {
                    charsetSlot = ch
                    state = State.ESC_CHARSET
                    return
                }
                // 注意：每个分支必须显式设定后继状态；不可在 when 之后统一置 GROUND，
                // 否则刚进入的 CSI / OSC / DCS 状态会被立刻覆盖，导致转义序列泄漏成文本。
                when (ch) {
                    '[' -> {
                        params.clear(); intermediates.clear(); privateMarker = ' '
                        state = State.CSI
                    }
                    ']' -> { oscBuf.clear(); state = State.OSC }
                    'P' -> { oscBuf.clear(); state = State.DCS }
                    '_' -> state = State.APC                          // APC：kitty 图形协议 → 消费丢弃
                    '%' -> state = State.ESC_PERCENT                  // ESC % G/@：UTF-8 指定
                    '7' -> { saveCursor(); state = State.GROUND }
                    '8' -> { restoreCursor(); state = State.GROUND }
                    'D' -> { lineFeed(); state = State.GROUND }
                    'M' -> { reverseIndex(); state = State.GROUND }
                    'E' -> { cursorCol = 0; lineFeed(); state = State.GROUND }
                    'H' -> { setTabStop(); state = State.GROUND }      // HTS
                    'c' -> { hardReset(); state = State.GROUND }
                    'N' -> { ssPending = 2; state = State.GROUND }     // SS2：下一字符取自 G2
                    'O' -> { ssPending = 3; state = State.GROUND }     // SS3：下一字符取自 G3
                    'n' -> { glSlot = 2; state = State.GROUND }        // LS2：GL ← G2
                    'o' -> { glSlot = 3; state = State.GROUND }        // LS3：GL ← G3
                    '}' -> { grSlot = 2; state = State.GROUND }        // LS2R：GR ← G2
                    '|' -> { grSlot = 3; state = State.GROUND }        // LS3R：GR ← G3
                    '~' -> { grSlot = 1; state = State.GROUND }        // LS1R：GR ← G1
                    else -> state = State.GROUND                        // DECKPAM/DECKPNM/其他：忽略
                }
            }

            State.CSI -> when {
                ch in '0'..'9' || ch == ';' || ch == ':' -> params.append(ch)
                ch == '?' || ch == '>' || ch == '<' || ch == '!' || ch == '=' -> privateMarker = ch
                ch in ' '..'/' -> intermediates.append(ch)   // 中间字节（含 DECSCUSR 的空格）
                ch in '@'..'~' -> {
                    handleCsi(ch, params.toString(), intermediates.toString())
                    state = State.GROUND
                }
                else -> Unit
            }

            State.OSC -> when (ch) {
                '\u0007' -> { applyOsc(oscBuf.toString()); state = State.GROUND }
                '\u001B' -> state = State.OSC_ESC
                // 超长 OSC（如 iTerm2 大图 base64）截断丢弃，防止内存被撑爆
                else -> if (oscBuf.length < MAX_OSC_LENGTH) oscBuf.append(ch)
            }

            State.OSC_ESC -> {
                if (ch == '\\') applyOsc(oscBuf.toString())
                state = State.GROUND
            }

            State.ESC_CHARSET -> {
                when (charsetSlot) {
                    '(' -> g0Charset = ch      // G0 指定（GL 默认来源）
                    ')' -> g1Charset = ch      // G1 指定
                    '*' -> g2Charset = ch      // G2 指定
                    '+' -> g3Charset = ch      // G3 指定
                }
                charsetSlot = ' '
                state = State.GROUND
            }

            State.ESC_PERCENT -> state = State.GROUND    // ESC % G / ESC % @：UTF-8 指定，消费即可

            State.DCS -> if (ch == '\u001B') state = State.DCS_ESC
            State.DCS_ESC -> state = State.GROUND   // DCS 忽略（含 kitty 图像）

            // APC（kitty 图形协议 `ESC _ G ... ST`）：完整消费载荷、绝不让 base64 落屏
            State.APC -> when {
                ch == '\u001B' -> state = State.APC_ESC
                ch == '\u0007' -> state = State.GROUND   // BEL 也可终止
                else -> Unit
            }
            State.APC_ESC -> state = when {
                ch == '\\' || ch == '\u0007' -> State.GROUND   // ST / BEL 终止
                ch == '\u001B' -> State.APC_ESC
                else -> State.APC
            }
        }
    }

    private var charsetSlot = ' '

    // ───────────────────────── OSC ─────────────────────────

    private fun applyOsc(value: String) {
        val idx = value.indexOf(';')
        val code = if (idx >= 0) value.substring(0, idx) else value
        val rest = if (idx >= 0) value.substring(idx + 1) else ""

        when (code) {
            "0", "1", "2" -> {
                title = rest
                onTitle(rest)
            }
            // iTerm2 内联图像（OSC 1337;File=...:base64）：
            // 载荷已由 OSC 状态机完整消费（BEL/ST 终止），此处仅显式确认丢弃。
            // 图像**渲染**属渲染层改动，本次不做——协议正确消费即保证屏幕不被污染。
            "1337" -> Unit
            "4" -> applyOscPalette(rest)
            "10" -> applyColorOsc(10, rest)
            "11" -> applyColorOsc(11, rest)
            "12" -> applyColorOsc(12, rest)
            "52" -> applyOscClipboard(rest)
            "104" -> if (rest.isBlank()) palette.resetAll() else rest.split(';').forEach { it.toIntOrNull()?.let(palette::resetPalette) }
            "110" -> palette.resetForeground()
            "111" -> palette.resetBackground()
            "112" -> palette.resetCursor()
            else -> Unit
        }
        bump()
    }

    /** OSC 4;index;color（可多组，逐对出现；color 为 `?` 时应答当前值） */
    private fun applyOscPalette(rest: String) {
        val parts = rest.split(';')
        var i = 0
        while (i + 1 < parts.size) {
            val index = parts[i].toIntOrNull() ?: run { i++; continue }
            val spec = parts[i + 1]
            if (spec == "?") {
                val rgb = palette.currentPaletteRgb(index)
                onResponse("\u001B]4;$index;rgb:${hex2(rgb ushr 16)}/${hex2((rgb ushr 8) and 0xFF)}/${hex2(rgb and 0xFF)}\u0007".toByteArray(Charsets.UTF_8))
            } else {
                parseColor(spec)?.let { palette.setPalette(index, it) }
            }
            i += 2
        }
    }

    /** OSC 10/11/12：设置或查询默认前景/背景/光标色 */
    private fun applyColorOsc(code: Int, rest: String) {
        val currentRgb: () -> Int = {
            val c = when (code) {
                10 -> palette.foreground()
                11 -> palette.background()
                else -> palette.cursor()
            }
            (c.red.toInt() shl 16) or (c.green.toInt() shl 8) or c.blue.toInt()
        }

        when {
            rest.startsWith("?") -> {
                val rgb = currentRgb()
                val spec = "rgb:${hex2(rgb ushr 16)}/${hex2((rgb ushr 8) and 0xFF)}/${hex2(rgb and 0xFF)}"
                onResponse("\u001B]$code;$spec\u0007".toByteArray(Charsets.UTF_8))
            }
            rest.isBlank() -> when (code) {
                10 -> palette.resetForeground()
                11 -> palette.resetBackground()
                else -> palette.resetCursor()
            }
            else -> parseColor(rest)?.let { rgb ->
                when (code) {
                    10 -> palette.setDefaultForeground(rgb)
                    11 -> palette.setDefaultBackground(rgb)
                    else -> palette.setCursorColor(rgb)
                }
            }
        }
    }

    /** 解析 `#RRGGBB` 或 `rgb:RR/GG/BB` */
    private fun parseColor(spec: String): Int? {
        val s = spec.trim()
        if (s.startsWith("#") && s.length == 7) return s.substring(1).toIntOrNull(16)
        if (s.startsWith("rgb:", ignoreCase = true)) {
            val parts = s.substring(4).split('/')
            if (parts.size == 3) {
                fun comp(v: String): Int {
                    val n = v.toIntOrNull(16) ?: return 0
                    // 支持 1~4 位十六进制分量
                    return when (v.length) {
                        1 -> n * 17
                        2 -> n
                        3 -> n shr 4
                        else -> n shr 8
                    }
                }
                return (comp(parts[0]) shl 16) or (comp(parts[1]) shl 8) or comp(parts[2])
            }
        }
        return null
    }

    /** OSC 52;c;base64 → 写系统剪贴板 */
    private fun applyOscClipboard(rest: String) {
        val idx = rest.indexOf(';')
        val payload = if (idx >= 0) rest.substring(idx + 1) else rest
        if (payload.isBlank() || payload == "?") return
        runCatching {
            val text = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
            if (text.isNotEmpty()) onClipboard(text)
        }
    }

    private fun hex2(v: Int): String = "%02x".format(v and 0xFF)

    // ───────────────────────── 字符输出 ─────────────────────────

    private fun putChar(ch0: Char) {
        val ch = mapCharset(ch0)
        lastPrintedChar = ch
        val width = terminalCharWidth(ch.code)
        if (width == 0) return

        if (width == 2 && cursorCol == active.cols - 1) {
            if (autoWrap) {
                // 宽字符整体换到下一行：当前行因换行被挤断 → 标记软换行（供 reflow 合并）
                active.screen[cursorRow].wrapped = true
                cursorCol = 0
                lineFeed()
            } else return
        }

        val row = active.screen[cursorRow]
        // 重新写入该行 → 不再是上一行的软换行续行
        row.wrapped = false
        if (insertMode) shiftRowRight(cursorCol, width)
        if (cursorCol in 0 until active.cols) {
            row.text[cursorCol] = ch
            row.styles[cursorCol] = currentStyle
            if (width == 2 && cursorCol + 1 < active.cols) {
                row.text[cursorCol + 1] = WIDE_PLACEHOLDER
                row.styles[cursorCol + 1] = currentStyle
            }
        }

        cursorCol += width
        if (cursorCol >= active.cols) {
            if (autoWrap) {
                cursorCol = 0
                lineFeed()
                row.wrapped = true
            } else {
                cursorCol = active.cols - 1
            }
        }
    }

    /**
     * 按当前字符集状态映射一个字符（DEC NRCS 完整切换）。
     *
     *  · GL 区（0x00-0x7F）按 glSlot 取 G0..G3（SO/SI 在 G0/G1 间切换，LS2/LS3 可指向 G2/G3）；
     *  · GR 区（0x80-0xFF）按 grSlot 取 G1..G3（LS1R/LS2R/LS3R 切换）；
     *  · SS2/SS3（ESC N / ESC O）只对下一个 GL 字符生效，且不消耗在 GR 字符上；
     *  · 未定义映射的字符原样返回 —— 天然兼作 Latin-1 / UTF-8 的兜底。
     */
    private fun mapCharset(ch: Char): Char {
        val code = ch.code
        if (ssPending != 0) {
            if (code < 0x80) {
                val cs = charsetOf(ssPending)
                ssPending = 0
                return mapViaCharset(cs, ch, code)
            }
            // GR 区字符不受 SS 影响（也不消耗 SS 状态）
        }
        val slot = if (code >= 0x80) grSlot else glSlot
        return mapViaCharset(charsetOf(slot), ch, code)
    }

    private fun charsetOf(slot: Int): Char = when (slot) {
        1 -> g1Charset
        2 -> g2Charset
        3 -> g3Charset
        else -> g0Charset
    }

    /** 查表映射：国家字符集表优先，其次 DEC 特殊图形（'0'） */
    private fun mapViaCharset(cs: Char, ch: Char, code: Int): Char {
        val pos = if (code >= 0x80) code - 0x80 else code
        NATIONAL_TABLES[cs]?.let { return it[pos] ?: ch }
        if (cs == '0') return DEC_GRAPHICS[pos] ?: ch
        return ch
    }

    private fun shiftRowRight(from: Int, count: Int) {
        val row = active.screen[cursorRow]
        val n = count.coerceAtMost(active.cols - from)
        for (x in active.cols - 1 downTo from + n) {
            row.text[x] = row.text[x - n]
            row.styles[x] = row.styles[x - n]
        }
        row.clear(from, from + n)
    }

    // ───────────────────────── 制表位 ─────────────────────────

    private fun nextTabStop() {
        var col = cursorCol + 1
        while (col < active.cols && !tabStops[col]) col++
        cursorCol = if (col >= active.cols) active.cols - 1 else col
    }

    private fun prevTabStop() {
        var col = cursorCol - 1
        while (col > 0 && !tabStops[col]) col--
        cursorCol = col.coerceAtLeast(0)
    }

    private fun setTabStop() {
        if (cursorCol in tabStops.indices) tabStops[cursorCol] = true
    }

    private fun clearTabStops(mode: Int) {
        when (mode) {
            0 -> if (cursorCol in tabStops.indices) tabStops[cursorCol] = false
            3 -> tabStops.fill(false)
        }
    }

    // ───────────────────────── 光标与滚动 ─────────────────────────

    private fun lineFeed() {
        if (cursorRow == scrollBottom || cursorRow >= active.rows - 1) {
            active.scrollUp(scrollTop, minOf(scrollBottom, active.rows - 1))
            if (cursorRow >= active.rows - 1) cursorRow = active.rows - 1
        } else {
            cursorRow++
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            active.scrollDown(scrollTop, scrollBottom)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun saveCursor() {
        val s = saved()
        s.row = cursorRow
        s.col = cursorCol
        s.fg = fgValue
        s.bg = bgValue
        s.fgRgb = fgIsRgb
        s.bgRgb = bgIsRgb
        s.flags = attrFlags
        s.g0 = g0Charset
        s.g1 = g1Charset
        s.g2 = g2Charset
        s.g3 = g3Charset
        s.gl = glSlot
        s.gr = grSlot
        s.origin = originMode
        s.autoWrapMode = autoWrap
    }

    private fun restoreCursor() {
        val s = saved()
        cursorRow = s.row.coerceIn(0, active.rows - 1)
        cursorCol = s.col.coerceIn(0, active.cols - 1)
        fgValue = s.fg
        bgValue = s.bg
        fgIsRgb = s.fgRgb
        bgIsRgb = s.bgRgb
        attrFlags = s.flags
        g0Charset = s.g0
        g1Charset = s.g1
        g2Charset = s.g2
        g3Charset = s.g3
        glSlot = s.gl
        grSlot = s.gr
        originMode = s.origin
        autoWrap = s.autoWrapMode
        rebuildStyle()
    }

    private fun hardReset() {
        primary.clearAll(); primary.clearScrollback()
        alternate.clearAll()
        active = primary
        resetAttributes()
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = active.rows - 1
        cursorVisible = true
        cursorStyleCode = 0
        mouseMode = MouseMode.NONE
        mouseEncoding = MouseEncoding.X10
        bracketedPaste = false
        autoWrap = true
        originMode = false
        insertMode = false
        appCursorKeys = false
        reverseVideo = false
        g0Charset = 'B'; g1Charset = 'B'; g2Charset = 'B'; g3Charset = 'B'
        glSlot = 0; grSlot = 1; ssPending = 0
        tabStops.fill(false)
        var c = 0
        while (c < tabStops.size) { tabStops[c] = true; c += 8 }
        mouseMode = MouseMode.NONE
        bump()
    }

    private fun resetAttributes() {
        fgIsRgb = false; fgValue = DEFAULT_COLOR
        bgIsRgb = false; bgValue = DEFAULT_COLOR
        attrFlags = 0L
        rebuildStyle()
    }

    // ───────────────────────── CSI ─────────────────────────

    private fun handleCsi(final: Char, raw: String, inter: String) {
        val args = if (raw.isEmpty()) emptyList() else raw.split(';').map { it.toIntOrNull() ?: 0 }

        when (final) {
            'm' -> applySgr(if (raw.isEmpty()) listOf(0) else args)
            'A' -> cursorRow = (cursorRow - max1(args)).coerceAtLeast(scrollTop)
            'B' -> cursorRow = (cursorRow + max1(args)).coerceAtMost(scrollBottom)
            'C' -> cursorCol = (cursorCol + max1(args)).coerceAtMost(active.cols - 1)
            'D' -> cursorCol = (cursorCol - max1(args)).coerceAtLeast(0)
            'E' -> { cursorRow = (cursorRow + max1(args)).coerceAtMost(scrollBottom); cursorCol = 0 }
            'F' -> { cursorRow = (cursorRow - max1(args)).coerceAtLeast(scrollTop); cursorCol = 0 }
            'G', '`' -> cursorCol = (max1(args) - 1).coerceIn(0, active.cols - 1)
            'd' -> cursorRow = (max1(args) - 1).coerceIn(0, active.rows - 1)
            'H', 'f' -> {
                val base = if (originMode) scrollTop else 0
                val r = (argAt(args, 0, 1) - 1) + base
                val c = argAt(args, 1, 1) - 1
                cursorRow = r.coerceIn(base, if (originMode) scrollBottom else active.rows - 1)
                cursorCol = c.coerceIn(0, active.cols - 1)
            }
            'J' -> eraseDisplay(argAt(args, 0, 0))
            'K' -> eraseLine(argAt(args, 0, 0))
            'L' -> insertLines(max1(args))
            'M' -> deleteLines(max1(args))
            'P' -> deleteChars(max1(args))
            '@' -> insertChars(max1(args))
            'X' -> eraseChars(max1(args))
            'S' -> repeat(max1(args)) { active.scrollUp(scrollTop, scrollBottom) }
            'T' -> repeat(max1(args)) { active.scrollDown(scrollTop, scrollBottom) }
            'b' -> repeat(max1(args).coerceAtMost(active.cols)) { putChar(lastPrintedChar) }   // REP
            'I' -> repeat(max1(args)) { nextTabStop() }                                        // CHT
            'Z' -> repeat(max1(args)) { prevTabStop() }                                        // CBT
            'g' -> clearTabStops(argAt(args, 0, 0))                                            // TBC
            's' -> saveCursor()
            'u' -> restoreCursor()
            'r' -> {
                scrollTop = (argAt(args, 0, 1) - 1).coerceIn(0, active.rows - 1)
                scrollBottom = (argAt(args, 1, active.rows) - 1).coerceIn(scrollTop, active.rows - 1)
                cursorRow = if (originMode) scrollTop else 0
                cursorCol = 0
            }
            'h' -> if (privateMarker == '?') applyModes(args, true)
            'l' -> if (privateMarker == '?') applyModes(args, false)
            'n' -> when (argAt(args, 0, 0)) {
                5 -> onResponse("\u001B[0n".toByteArray(Charsets.UTF_8))
                6 -> reportCursor(false)
            }
            'c' -> when (privateMarker) {
                '>' -> onResponse("\u001B[>41;354;0c".toByteArray(Charsets.UTF_8))   // DA2
                else -> onResponse("\u001B[?62;4;6;22c".toByteArray(Charsets.UTF_8)) // DA1（VT220 + 色 + 制表）
            }
            'q' -> if (inter == " ") cursorStyleCode = argAt(args, 0, 0)              // DECSCUSR
            'p' -> when {
                inter == "!" -> softReset()                                          // DECSTR
                inter == "$" -> Unit                                                  // DECRQM：忽略
                else -> Unit
            }
            't' -> Unit                                                              // 窗口操作：忽略
            else -> Unit
        }
    }

    private fun reportCursor(alt: Boolean) {
        val prefix = if (alt) "?" else ""
        onResponse("\u001B[$prefix${cursorRow + 1};${cursorCol + 1}R".toByteArray(Charsets.UTF_8))
    }

    /** DECSET / DECRST */
    private fun applyModes(modes: List<Int>, enable: Boolean) {
        modes.forEach { mode ->
            when (mode) {
                1 -> appCursorKeys = enable
                3 -> {
                    // DECCOLM：切换 80/132 列（同时清屏）。
                    // 尺寸请求经 onResizeRequest → 会话 resize → 模拟器 resize，
                    // 主屏将按新宽度自动 reflow（列数变化触发重排）。
                    val target = if (enable) 132 else 80
                    if (target != active.cols) onResizeRequest(active.rows, target)
                    cursorRow = 0; cursorCol = 0
                    active.clearAll()
                }
                5 -> reverseVideo = enable
                6 -> {
                    originMode = enable
                    cursorRow = if (enable) scrollTop else 0
                    cursorCol = 0
                }
                7 -> autoWrap = enable
                25 -> cursorVisible = enable
                47 -> switchAltScreen(enable, clear = true, saveCursor = false)
                1047 -> switchAltScreen(enable, clear = true, saveCursor = false)
                1048 -> if (enable) saveCursor() else restoreCursor()
                1049 -> switchAltScreen(enable, clear = true, saveCursor = true)
                66 -> Unit
                1000 -> mouseMode = if (enable) MouseMode.CLICK else MouseMode.NONE
                1002 -> mouseMode = if (enable) MouseMode.DRAG else MouseMode.NONE
                1003 -> mouseMode = if (enable) MouseMode.ANY else MouseMode.NONE
                1004 -> Unit
                // 编码协议互斥：开启一个自动关闭其它（与 xterm 一致）
                1005 -> setMouseEncoding(enable, MouseEncoding.UTF8)
                1006 -> setMouseEncoding(enable, MouseEncoding.SGR)
                1015 -> setMouseEncoding(enable, MouseEncoding.URXVT)
                1016 -> setMouseEncoding(enable, MouseEncoding.SGR_PIXEL)
                2004 -> bracketedPaste = enable
                else -> Unit
            }
        }
    }

    /** 切换鼠标编码协议：开启时互斥覆盖；关闭时若正处该协议则退回 X10 */
    private fun setMouseEncoding(enable: Boolean, encoding: MouseEncoding) {
        if (enable) mouseEncoding = encoding
        else if (mouseEncoding == encoding) mouseEncoding = MouseEncoding.X10
    }

    /**
     * 备用屏切换。
     * 进入时保存光标与滚动区域并清屏；退出时恢复主屏与光标。
     */
    private fun switchAltScreen(enable: Boolean, clear: Boolean, saveCursor: Boolean) {
        val wantAlt = enable
        val currentlyAlt = active === alternate
        if (wantAlt == currentlyAlt) {
            if (enable && clear) active.clearAll()
            return
        }
        if (enable) {
            if (saveCursor) saveCursor()
            scrollTopSaved = scrollTop
            scrollBottomSaved = scrollBottom
            active = alternate
            if (clear) alternate.clearAll()
        } else {
            active = primary
            if (saveCursor) restoreCursor()
            scrollTop = scrollTopSaved
            scrollBottom = scrollBottomSaved
        }
        cursorRow = cursorRow.coerceIn(0, active.rows - 1)
        cursorCol = cursorCol.coerceIn(0, active.cols - 1)
        scrollTop = scrollTop.coerceIn(0, active.rows - 1)
        scrollBottom = scrollBottom.coerceIn(scrollTop, active.rows - 1)
    }

    private var scrollTopSaved = 0
    private var scrollBottomSaved = 0

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (y in cursorRow + 1 until active.rows) active.screen[y].clear(style = currentStyle)
            }
            1 -> {
                eraseLine(1)
                for (y in 0 until cursorRow) active.screen[y].clear(style = currentStyle)
            }
            2 -> active.screen.forEach { it.clear(style = currentStyle) }
            3 -> primary.clearScrollback()
        }
    }

    private fun eraseLine(mode: Int) {
        val row = active.screen[cursorRow]
        when (mode) {
            0 -> row.clear(cursorCol, active.cols, currentStyle)
            1 -> row.clear(0, cursorCol + 1, currentStyle)
            2 -> row.clear(0, active.cols, currentStyle)
        }
    }

    private fun insertLines(count: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (y in scrollBottom downTo cursorRow + 1) active.screen[y].copyFrom(active.screen[y - 1])
            active.screen[cursorRow].clear()
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (y in cursorRow until scrollBottom) active.screen[y].copyFrom(active.screen[y + 1])
            active.screen[scrollBottom].clear()
        }
    }

    private fun deleteChars(count: Int) {
        val row = active.screen[cursorRow]
        val n = count.coerceAtMost(active.cols - cursorCol)
        for (x in cursorCol until active.cols - n) {
            row.text[x] = row.text[x + n]
            row.styles[x] = row.styles[x + n]
        }
        row.clear(active.cols - n, active.cols)
    }

    private fun insertChars(count: Int) {
        val row = active.screen[cursorRow]
        val n = count.coerceAtMost(active.cols - cursorCol)
        for (x in active.cols - 1 downTo cursorCol + n) {
            row.text[x] = row.text[x - n]
            row.styles[x] = row.styles[x - n]
        }
        row.clear(cursorCol, cursorCol + n)
    }

    private fun eraseChars(count: Int) {
        active.screen[cursorRow].clear(cursorCol, (cursorCol + count).coerceAtMost(active.cols), currentStyle)
    }

    private fun max1(args: List<Int>): Int = argAt(args, 0, 1).coerceAtLeast(1)

    private fun argAt(args: List<Int>, index: Int, default: Int): Int {
        if (index >= args.size) return default
        val v = args[index]
        return if (v == 0) default else v
    }

    // ───────────────────────── SGR ─────────────────────────

    private fun applySgr(args: List<Int>) {
        var i = 0
        while (i < args.size) {
            when (val code = args[i]) {
                0 -> resetAttributes()
                1 -> attrFlags = attrFlags or Style.FLAG_BOLD
                2 -> attrFlags = attrFlags or Style.FLAG_DIM
                3 -> attrFlags = attrFlags or Style.FLAG_ITALIC
                4 -> attrFlags = attrFlags or Style.FLAG_UNDERLINE
                5, 6 -> attrFlags = attrFlags or Style.FLAG_BLINK
                7 -> attrFlags = attrFlags or Style.FLAG_REVERSE
                8 -> attrFlags = attrFlags or Style.FLAG_INVISIBLE
                9 -> attrFlags = attrFlags or Style.FLAG_STRIKE
                21 -> attrFlags = attrFlags and Style.FLAG_BOLD.inv()
                22 -> attrFlags = attrFlags and (Style.FLAG_BOLD or Style.FLAG_DIM).inv()
                23 -> attrFlags = attrFlags and Style.FLAG_ITALIC.inv()
                24 -> attrFlags = attrFlags and Style.FLAG_UNDERLINE.inv()
                25 -> attrFlags = attrFlags and Style.FLAG_BLINK.inv()
                27 -> attrFlags = attrFlags and Style.FLAG_REVERSE.inv()
                28 -> attrFlags = attrFlags and Style.FLAG_INVISIBLE.inv()
                29 -> attrFlags = attrFlags and Style.FLAG_STRIKE.inv()
                in 30..37 -> setFgIndex(code - 30)
                in 40..47 -> setBgIndex(code - 40)
                39 -> setFgIndex(DEFAULT_COLOR)
                49 -> setBgIndex(DEFAULT_COLOR)
                in 90..97 -> setFgIndex(code - 90 + 8)
                in 100..107 -> setBgIndex(code - 100 + 8)
                38, 48 -> {
                    val isFg = code == 38
                    when (args.getOrNull(i + 1)) {
                        5 -> {
                            val n = args.getOrNull(i + 2) ?: 0
                            if (isFg) setFgIndex(n.coerceIn(0, 255)) else setBgIndex(n.coerceIn(0, 255))
                            i += 2
                        }
                        2 -> {
                            val r = (args.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                            val g = (args.getOrNull(i + 3) ?: 0).coerceIn(0, 255)
                            val b = (args.getOrNull(i + 4) ?: 0).coerceIn(0, 255)
                            if (isFg) setFgRgb((r shl 16) or (g shl 8) or b)
                            else setBgRgb((r shl 16) or (g shl 8) or b)
                            i += 4
                        }
                    }
                }
                58 -> {
                    // 下划线颜色：方案A 忽略取值但消费参数，避免错位
                    when (args.getOrNull(i + 1)) {
                        5 -> i += 2
                        2 -> i += 4
                    }
                }
            }
            i++
        }
        rebuildStyle()
    }

    private fun setFgIndex(index: Int) {
        fgIsRgb = false
        fgValue = index
    }

    private fun setBgIndex(index: Int) {
        bgIsRgb = false
        bgValue = index
    }

    private fun setFgRgb(rgb: Int) {
        if (!trueColorEnabled) { setFgIndex(rgbToAnsi256((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)); return }
        fgIsRgb = true
        fgValue = rgb
    }

    private fun setBgRgb(rgb: Int) {
        if (!trueColorEnabled) { setBgIndex(rgbToAnsi256((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)); return }
        bgIsRgb = true
        bgValue = rgb
    }

    private fun rebuildStyle() {
        var style = if (fgIsRgb) Style.withFgRgb(fgValue) else Style.withFg(fgValue)
        style = if (bgIsRgb) Style.withBgRgb(style, bgValue) else Style.withBg(style, bgValue)
        style = style or attrFlags
        if (reverseVideo) style = Style.withFlag(style, Style.FLAG_REVERSE, true)
        currentStyle = style
    }

    // ───────────────────────── 暴露给渲染层的查询 ─────────────────────────

    fun currentAttrFlags(): Long = attrFlags

    fun reverseVideoEnabled(): Boolean = reverseVideo

    companion object {
        /** OSC 载荷最大缓冲长度（字符）；超长直接丢弃，防止超大 base64 载荷撑爆内存 */
        private const val MAX_OSC_LENGTH = 1_000_000

        /** DEC 特殊图形字符集（G0-G3 = '0'）的映射表 */
        private val DEC_GRAPHICS = mapOf(
            0x60 to '◆', 0x61 to '▒', 0x62 to '␉', 0x63 to '␌', 0x64 to '␍',
            0x65 to '␊', 0x66 to '°', 0x67 to '±', 0x68 to '␤', 0x69 to '␋',
            0x6A to '┘', 0x6B to '┐', 0x6C to '┌', 0x6D to '└', 0x6E to '┼',
            0x6F to '⎺', 0x70 to '⎻', 0x71 to '─', 0x72 to '⎼', 0x73 to '⎽',
            0x74 to '├', 0x75 to '┤', 0x76 to '┴', 0x77 to '┬', 0x78 to '│',
            0x79 to '≤', 0x7A to '≥', 0x7B to 'π', 0x7C to '≠', 0x7D to '£',
            0x7E to '·',
        )

        /** 简写：构造一个国家字符集映射表（DEC NRCS 的可替换字符位） */
        private fun charsetTable(vararg pairs: Pair<Int, Char>): Map<Int, Char> = mapOf(*pairs)

        /**
         * DEC 国家字符集（NRCS）映射表：设计符 → (字符码 → 替换字符)。
         * 覆盖 DEC VT220 全部标准国家字符集；未列出的设计符（如 'B' 美式 ASCII）
         * 走 [mapViaCharset] 的原样兜底（即 Latin-1 直通）。
         */
        private val NATIONAL_TABLES: Map<Char, Map<Int, Char>> = buildMap {
            put('A', charsetTable(0x23 to '£'))   // UK 英国：仅 # → £

            // 荷兰
            put('4', charsetTable(
                0x23 to '£', 0x40 to '¾', 0x5B to 'ĳ', 0x5C to '½', 0x5D to '|',
                0x7B to '¨', 0x7C to 'ƒ', 0x7D to '¼', 0x7E to '´',
            ))

            // 芬兰（'5' 与 'C' 同表）
            val finnish = charsetTable(
                0x5B to 'Ä', 0x5C to 'Ö', 0x5D to 'Å', 0x5E to 'Ü', 0x60 to 'é',
                0x7B to 'ä', 0x7C to 'ö', 0x7D to 'å', 0x7E to 'ü',
            )
            put('5', finnish)
            put('C', finnish)

            // 法国（'R' 与 'f' 同表）
            val french = charsetTable(
                0x23 to '£', 0x40 to 'à', 0x5B to '°', 0x5C to 'ç', 0x5D to '§',
                0x7B to 'é', 0x7C to 'ù', 0x7D to 'è', 0x7E to '¨',
            )
            put('R', french)
            put('f', french)

            // 法裔加拿大
            put('Q', charsetTable(
                0x40 to 'à', 0x5B to 'â', 0x5C to 'ç', 0x5D to 'ê', 0x5E to 'î',
                0x60 to 'ô', 0x7B to 'é', 0x7C to 'ù', 0x7D to 'è', 0x7E to 'û',
            ))

            // 德国
            put('K', charsetTable(
                0x40 to '§', 0x5B to 'Ä', 0x5C to 'Ö', 0x5D to 'Ü',
                0x7B to 'ä', 0x7C to 'ö', 0x7D to 'ü', 0x7E to 'ß',
            ))

            // 意大利
            put('Y', charsetTable(
                0x23 to '£', 0x40 to '§', 0x5B to '°', 0x5C to 'ç', 0x5D to 'é',
                0x60 to 'ù', 0x7B to 'à', 0x7C to 'ò', 0x7D to 'è', 0x7E to 'ì',
            ))

            // 挪威 / 丹麦（'E' 与 '6' 同表）
            val norwegian = charsetTable(
                0x40 to 'Ä', 0x5B to 'Æ', 0x5C to 'Ø', 0x5D to 'Å', 0x5E to 'Ü',
                0x60 to 'é', 0x7B to 'ä', 0x7C to 'ø', 0x7D to 'å', 0x7E to 'ü',
            )
            put('E', norwegian)
            put('6', norwegian)

            // 西班牙
            put('Z', charsetTable(
                0x23 to '£', 0x40 to '§', 0x5B to '¡', 0x5C to 'Ñ', 0x5D to '¿',
                0x7B to '°', 0x7C to 'ñ', 0x7D to 'ç',
            ))

            // 瑞典（'H' 与 '7' 同表）
            val swedish = charsetTable(
                0x40 to 'É', 0x5B to 'Ä', 0x5C to 'Ö', 0x5D to 'Å', 0x5E to 'Ü',
                0x60 to 'é', 0x7B to 'ä', 0x7C to 'ö', 0x7D to 'å', 0x7E to 'ü',
            )
            put('H', swedish)
            put('7', swedish)

            // 瑞士
            put('=', charsetTable(
                0x23 to 'ù', 0x40 to 'à', 0x5B to 'é', 0x5C to 'ç', 0x5D to 'ê',
                0x5E to 'î', 0x60 to 'ô', 0x7B to 'ä', 0x7C to 'ö', 0x7D to 'ü', 0x7E to 'û',
            ))
        }
    }
}
