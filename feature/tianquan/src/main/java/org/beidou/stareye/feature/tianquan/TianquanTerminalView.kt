package org.beidou.stareye.feature.tianquan

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.theme.beidouShape

/** 选择范围（行号以「回滚历史 + 屏幕」合并后的绝对行序为准） */
data class TerminalSelection(
    val anchorLine: Int,
    val anchorCol: Int,
    val focusLine: Int,
    val focusCol: Int,
) {
    val startLine get() = minOf(anchorLine, focusLine)
    val endLine get() = maxOf(anchorLine, focusLine)

    fun startColOf(line: Int): Int = when {
        startLine == endLine -> minOf(anchorCol, focusCol)
        line == startLine -> if (anchorLine <= focusLine) anchorCol else focusCol
        else -> 0
    }

    fun endColOf(line: Int): Int = when {
        startLine == endLine -> maxOf(anchorCol, focusCol)
        line == endLine -> if (anchorLine <= focusLine) focusCol else anchorCol
        else -> Int.MAX_VALUE
    }

    fun contains(line: Int, col: Int): Boolean =
        line in startLine..endLine && col >= startColOf(line) && col < endColOf(line)
}

/** 网格度量：把像素坐标换算成终端行列 */
class TerminalMetrics(val charWidth: Float, val rowHeight: Float)

/**
 * 天权中枢 —— 终端渲染视图（自研实现）。
 *
 * 负责：按调色板渲染彩色行、光标（方块/下划线/竖条，可闪烁）、选区高亮、
 * 文本选择（长按拖动）、点击上报鼠标事件、双指缩放回调。
 */
@Composable
fun TianquanTerminalView(
    session: TianquanSession?,
    revision: Int,
    fontSize: Int,
    cursorStyle: CursorStyle,
    cursorBlinkMs: Int,
    selection: TerminalSelection?,
    onSelectionChange: (TerminalSelection?) -> Unit,
    onMetrics: (TerminalMetrics) -> Unit,
    onTap: (line: Int, col: Int) -> Unit,
    onLongPressStart: (line: Int, col: Int) -> Unit,
    onDragSelect: (line: Int, col: Int) -> Unit,
    onPinch: (deltaFontSize: Int) -> Unit,
    modifier: Modifier = Modifier,
    statusText: String,
) {
    val emulator = session?.emulator
    val palette = emulator?.palette ?: remember { TerminalPalette(TerminalThemes.all.first()) }
    val background = palette.background()
    val listState = rememberLazyListState()

    // 光标闪烁相位
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(cursorBlinkMs, revision) {
        if (cursorBlinkMs <= 0) {
            blinkOn = true
            return@LaunchedEffect
        }
        blinkOn = true
        while (true) {
            kotlinx.coroutines.delay(cursorBlinkMs.toLong())
            blinkOn = !blinkOn
        }
    }

    val buffer = emulator?.buffer
    val scrollbackCount = buffer?.scrollback?.size ?: 0

    val lines: List<AnnotatedString> = remember(revision, fontSize, palette.theme, blinkOn, selection, emulator?.cursorStyleCode) {
        if (emulator == null || buffer == null) {
            emptyList()
        } else {
            val out = ArrayList<AnnotatedString>(buffer.scrollback.size + buffer.rows)
            buffer.scrollback.forEachIndexed { i, row ->
                out += renderRow(row, palette, -1, cursorStyle, false, selection, i, scrollbackCount)
            }
            for (y in 0 until buffer.rows) {
                val abs = scrollbackCount + y
                val cursorAt = if (emulator.cursorVisible && y == emulator.cursorRow && blinkOn) emulator.cursorCol else -1
                val effCursor = effectiveCursorStyle(cursorStyle, emulator.cursorStyleCode)
                out += renderRow(buffer.screen[y], palette, cursorAt, effCursor, true, selection, abs, scrollbackCount)
            }
            out
        }
    }

    Box(
        modifier = modifier
            .clip(beidouShape(16.dp))
            .background(background),
    ) {
        if (emulator == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StarGlyphSmall(palette.cursor())
                    androidx.compose.material3.Text(
                        statusText,
                        color = palette.foreground().copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
            }
            return@Box
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                // key 用会话实例而非 revision：revision 每帧都变，会导致手势检测被反复重启、吞掉点击
                .pointerInput(session) {
                    detectTapGestures(
                        onTap = { offset ->
                            val (line, col) = toCell(offset, listState, fontSize)
                            onTap(line, col)
                        },
                    )
                }
                .pointerInput(session) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val (line, col) = toCell(offset, listState, fontSize)
                            onLongPressStart(line, col)
                        },
                        onDrag = { change, _ ->
                            val (line, col) = toCell(change.position, listState, fontSize)
                            onDragSelect(line, col)
                            change.consume()
                        },
                    )
                },
        ) {
            items(lines.size) { index ->
                BasicText(
                    text = lines[index],
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.35f).sp,
                        color = palette.foreground(),
                    ),
                )
            }
        }

        // 更新后自动滚到底部（选择中不打扰）
        LaunchedEffect(revision) {
            if (lines.isNotEmpty() && selection == null) listState.scrollToItem(lines.lastIndex)
        }

        // 度量回报（供上层换算网格尺寸）
        androidx.compose.runtime.LaunchedEffect(fontSize) {
            onMetrics(TerminalMetrics(fontSize * 0.6f, fontSize * 1.35f))
        }
    }
}

@Composable
private fun StarGlyphSmall(color: Color) {
    StarGlyph(color = color, size = 28.dp)
}

/** 像素坐标 → 网格坐标（绝对行序，含回滚历史） */
private fun toCell(offset: Offset, listState: androidx.compose.foundation.lazy.LazyListState, fontSize: Int): Pair<Int, Int> {
    val rowH = fontSize * 1.35f
    val charW = fontSize * 0.6f
    val scrolled = listState.firstVisibleItemIndex * rowH - listState.firstVisibleItemScrollOffset
    val line = ((offset.y + scrolled - 6f) / rowH).toInt().coerceAtLeast(0)
    val col = ((offset.x - 6f) / charW).toInt().coerceAtLeast(0)
    return line to col
}

/** 程序请求的光标样式（DECSCUSR）优先于用户设置 */
private fun effectiveCursorStyle(user: CursorStyle, decscusr: Int): CursorStyle = when (decscusr) {
    1, 2 -> CursorStyle.BLOCK
    3, 4 -> CursorStyle.UNDERLINE
    5, 6 -> CursorStyle.BAR
    else -> user
}

/**
 * 渲染一行：按「样式 + 光标 + 选区」分组以减少 span 数量。
 */
private fun renderRow(
    row: TerminalRow,
    palette: TerminalPalette,
    cursorCol: Int,
    cursorStyle: CursorStyle,
    isScreenRow: Boolean,
    selection: TerminalSelection?,
    absLine: Int,
    scrollbackCount: Int,
): AnnotatedString {
    val selStart = selection?.startColOf(absLine) ?: Int.MAX_VALUE
    val selEnd = selection?.endColOf(absLine) ?: Int.MIN_VALUE
    val inSel = selection != null && absLine in selection.startLine..selection.endLine

    var len = row.trimmedLength()
    if (cursorCol >= 0) len = maxOf(len, cursorCol + 1)
    if (inSel) len = maxOf(len, minOf(selEnd, row.cols))
    if (len == 0) return AnnotatedString("")

    return buildAnnotatedString {
        var i = 0
        while (i < len) {
            val style = row.styles[i]
            val cur = i == cursorCol
            val sel = inSel && i >= selStart && i < selEnd
            val sb = StringBuilder()
            var j = i
            while (
                j < len &&
                row.styles[j] == style &&
                (j == cursorCol) == cur &&
                (inSel && j >= selStart && j < selEnd) == sel
            ) {
                val c = row.text[j]
                if (c != WIDE_PLACEHOLDER) sb.append(c)
                j++
            }
            if (sb.isNotEmpty()) {
                withStyle(spanFor(style, palette, cur, cursorStyle, sel, isScreenRow)) {
                    append(sb.toString())
                }
            }
            i = j
        }
    }
}

private fun spanFor(
    style: Long,
    palette: TerminalPalette,
    isCursor: Boolean,
    cursorStyle: CursorStyle,
    isSelected: Boolean,
    isScreenRow: Boolean,
): SpanStyle {
    val reverse = Style.has(style, Style.FLAG_REVERSE)
    val invisible = Style.has(style, Style.FLAG_INVISIBLE)

    val fg = when {
        Style.fgIsRgb(style) -> rgbOf(Style.fgRgb(style))
        else -> palette.resolve(Style.fgIndex(style), true)
    }
    val bg = when {
        Style.bgIsRgb(style) -> rgbOf(Style.bgRgb(style))
        else -> palette.resolve(Style.bgIndex(style), false)
    }

    var textColor = if (reverse) bg else fg
    var backgroundColor = if (reverse) fg else bg

    if (isSelected) {
        // 选区：以光标色做半透明覆盖，保持可读性
        backgroundColor = palette.cursor().copy(alpha = 0.35f)
    }

    if (isCursor) {
        textColor = palette.background()
        backgroundColor = palette.cursor()
    }

    if (invisible) textColor = Color.Transparent

    val dim = Style.has(style, Style.FLAG_DIM)
    if (dim) textColor = textColor.copy(alpha = 0.6f)

    return SpanStyle(
        color = textColor,
        background = if (backgroundColor == palette.background()) Color.Unspecified else backgroundColor,
        fontWeight = if (Style.has(style, Style.FLAG_BOLD)) FontWeight.Bold else null,
        textDecoration = when {
            Style.has(style, Style.FLAG_UNDERLINE) && Style.has(style, Style.FLAG_STRIKE) ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            Style.has(style, Style.FLAG_UNDERLINE) -> TextDecoration.Underline
            Style.has(style, Style.FLAG_STRIKE) -> TextDecoration.LineThrough
            else -> null
        },
    )
}

private fun rgbOf(rgb: Int): Color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

/** 未使用但保留：把密度换算成网格行列 */
@Suppress("unused")
internal fun gridSize(widthPx: Int, heightPx: Int, density: Density, fontSize: Int): Pair<Int, Int> {
    val charW = with(density) { (fontSize * 0.6f).dp.toPx() }
    val rowH = with(density) { (fontSize * 1.35f).dp.toPx() }
    return (widthPx / charW).toInt() to (heightPx / rowH).toInt()
}
