package org.beidou.stareye.feature.tianquan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/** 输入框文本的同步镜像（非 Compose 状态，供 diff 即时读取） */
private class TextMirror {
    var value: String = ""
}

/**
 * 天权中枢 —— Termux-X（终端模拟器）界面。
 *
 * 方案A+：自研终端模拟器（备用屏 / 鼠标上报 / 真彩 / OSC / 制表位 / 线绘字符集）
 * + 原生 pty 会话 + 多会话切换 + 文本选择复制 + 附加键排 + 可配置配色字号。
 */
@Composable
fun TianquanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianquanFeature.star.order)

    var sessions by remember { mutableStateOf<List<TianquanSession>>(emptyList()) }
    var activeIndex by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("正在启动终端…") }
    var selection by remember { mutableStateOf<TerminalSelection?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showFileService by remember { mutableStateOf(false) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val mirror = remember { TextMirror() }
    var channel by remember { mutableStateOf(TianquanPrefs.channel(context)) }

    // 无可见输入框：点击终端即聚焦这个隐形字段并唤起软键盘，输入直接写入 pty
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var fontSize by remember { mutableIntStateOf(TianquanPrefs.fontSize(context)) }
    var cursorStyle by remember { mutableStateOf(TianquanPrefs.cursorStyle(context)) }
    var cursorBlink by remember { mutableIntStateOf(TianquanPrefs.cursorBlinkMs(context)) }
    var theme by remember { mutableStateOf(TianquanPrefs.theme(context)) }
    var gridRows by remember { mutableIntStateOf(24) }
    var gridCols by remember { mutableIntStateOf(80) }

    val palette = remember(theme) { TerminalPalette(theme) }

    val active = sessions.getOrNull(activeIndex)

    fun startSession() {
        scope.launch {
            val s = TianquanSession(
                context = context,
                shellPath = TianquanPrefs.shellPath(context),
                rows = gridRows,
                cols = gridCols,
                palette = palette,
                channel = TianquanPrefs.channel(context),
                scrollbackRows = TianquanPrefs.scrollbackRows(context),
                trueColor = TianquanPrefs.trueColor(context),
                onUpdate = { revision++ },
                onExit = { msg -> status = msg },
                onClipboard = { text ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("beidou", text))
                },
                onBell = {
                    if (TianquanPrefs.bellVibrate(context)) {
                        runCatching {
                            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            v.vibrate(30)
                        }
                    }
                },
                onResizeRequest = { rows, cols ->
                    gridRows = rows
                    gridCols = cols
                },
            )
            val result = withContext(Dispatchers.IO) { s.start() }
            sessions = sessions + s
            activeIndex = sessions.size - 1
            status = result.fold(
                onSuccess = { s.modeLabel },
                onFailure = { "启动失败：${it.message ?: "未知错误"}" },
            )
            revision++
        }
    }

    LaunchedEffect(Unit) { startSession() }

    DisposableEffect(Unit) {
        onDispose {
            val toKill = sessions
            sessions = emptyList()
            scope.launch(Dispatchers.IO) { toKill.forEach { it.destroy() } }
        }
    }

    LaunchedEffect(gridRows, gridCols) { active?.resize(gridRows, gridCols) }

    fun send(text: String) {
        active?.write(text)
        revision++
    }

    fun sendByte(value: Int) {
        active?.write(byteArrayOf(value.toByte()))
        revision++
    }

    fun closeSession(target: TianquanSession) {
        scope.launch {
            withContext(Dispatchers.IO) { target.destroy() }
            sessions = sessions - target
            if (activeIndex >= sessions.size) activeIndex = (sessions.size - 1).coerceAtLeast(0)
            revision++
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(TianquanFeature.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    active?.emulator?.title?.takeIf { it.isNotBlank() } ?: status,
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
            Chip("会话 ${sessions.size}", accent) { showSessions = true }
            Spacer(Modifier.width(6.dp))
            Chip("+", accent) { startSession() }
            Spacer(Modifier.width(6.dp))
            Chip("文件", accent) { showFileService = true }
            Spacer(Modifier.width(6.dp))
            Chip("设置", accent) { showSettings = true }
        }

        Spacer(Modifier.height(10.dp))

        // ── 终端区 ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val charWpx = with(density) { (fontSize * 0.6f).dp.toPx() }
            val rowHpx = with(density) { (fontSize * 1.35f).dp.toPx() }
            LaunchedEffect(constraints.maxWidth, constraints.maxHeight, charWpx, rowHpx) {
                val cols = (constraints.maxWidth / charWpx).toInt().coerceIn(20, 300)
                val rows = (constraints.maxHeight / rowHpx).toInt().coerceIn(5, 200)
                if (cols != gridCols) gridCols = cols
                if (rows != gridRows) gridRows = rows
            }

            TianquanTerminalView(
                session = active,
                revision = revision,
                fontSize = fontSize,
                cursorStyle = cursorStyle,
                cursorBlinkMs = cursorBlink,
                selection = selection,
                onSelectionChange = { selection = it },
                onMetrics = { },
                onTap = { line, col ->
                    val emu = active?.emulator
                    val mouseActive = emu != null && emu.mouseMode != MouseMode.NONE &&
                        TianquanPrefs.mouseTrackingEnabled(context)
                    if (mouseActive) {
                        val screenLine = line - emu!!.buffer.scrollback.size
                        if (screenLine >= 0) {
                            emu.reportMouse(MouseButton.LEFT, col, screenLine, press = true, motion = false)
                            emu.reportMouse(MouseButton.LEFT, col, screenLine, press = false, motion = false)
                            revision++
                        }
                    } else {
                        // 点击终端 = 直接在命令行输入：聚焦隐形字段并唤起软键盘
                        runCatching { inputFocus.requestFocus() }
                        keyboard?.show()
                    }
                },
                onLongPressStart = { line, col -> selection = TerminalSelection(line, col, line, col) },
                onDragSelect = { line, col ->
                    selection = selection?.copy(focusLine = line, focusCol = col)
                },
                onPinch = { },
                statusText = status,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── 选择工具条 ──
        if (selection != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("复制", accent) {
                    val text = extractSelection(active, selection!!)
                    if (text.isNotBlank()) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("beidou", text))
                        Toast.makeText(context, "已复制 ${text.length} 字符", Toast.LENGTH_SHORT).show()
                    }
                    selection = null
                }
                Chip("全选", accent) {
                    val emu = active?.emulator ?: return@Chip
                    val last = emu.buffer.scrollback.size + emu.buffer.rows - 1
                    selection = TerminalSelection(0, 0, last, emu.buffer.cols)
                }
                Chip("粘贴", accent) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isNotEmpty()) active?.emulator?.paste(text)
                    selection = null
                    revision++
                }
                Chip("取消", Color.Gray) { selection = null }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 附加键排 ──
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyButton("Esc", accent) { sendByte(0x1B) }
                KeyButton("Tab", accent) { sendByte(0x09) }
                KeyButton(if (ctrlArmed) "Ctrl•" else "Ctrl", if (ctrlArmed) palette.cursor() else accent) { ctrlArmed = !ctrlArmed }
                KeyButton("←", accent) { send(active?.emulator?.arrowKeySequence('D') ?: "\u001B[D") }
                KeyButton("↑", accent) { send(active?.emulator?.arrowKeySequence('A') ?: "\u001B[A") }
                KeyButton("↓", accent) { send(active?.emulator?.arrowKeySequence('B') ?: "\u001B[B") }
                KeyButton("→", accent) { send(active?.emulator?.arrowKeySequence('C') ?: "\u001B[C") }
                KeyButton("^C", accent) { sendByte(0x03) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyButton("⏎", accent) {
                    sendByte(0x0D)
                    mirror.value = ""
                    input = ""
                }
                KeyButton("^D", accent) { sendByte(0x04) }
                KeyButton("^L", accent) { sendByte(0x0C) }
                KeyButton("^Z", accent) { sendByte(0x1A) }
                KeyButton("^A", accent) { sendByte(0x01) }
                KeyButton("^E", accent) { sendByte(0x05) }
                KeyButton("/", accent) { sendByte('/'.code) }
                KeyButton("-", accent) { sendByte('-'.code) }
                KeyButton("|", accent) { sendByte('|'.code) }
                KeyButton("~", accent) { sendByte('~'.code) }
                KeyButton("⌨", accent) {
                    runCatching { inputFocus.requestFocus() }
                    keyboard?.show()
                }
                KeyButton("A-", accent) { fontSize = (fontSize - 1).coerceAtLeast(6); TianquanPrefs.setFontSize(context, fontSize) }
                KeyButton("A+", accent) { fontSize = (fontSize + 1).coerceAtMost(32); TianquanPrefs.setFontSize(context, fontSize) }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 隐形输入捕获 ──
        // 界面上没有输入框：点击终端区域即聚焦此字段并唤起软键盘，
        // 键入内容直接送入 pty（回显由终端负责），从而"在命令行上直接输入"。
        BasicTextField(
            value = input,
            onValueChange = { new ->
                val old = mirror.value
                when {
                    new.length > old.length -> {
                        val added = new.substring(old.length)
                        for (c in added) {
                            when {
                                // 软键盘回车：终端要的是 CR 而非 LF
                                c == '\n' || c == '\r' -> sendByte(0x0D)
                                ctrlArmed -> {
                                    sendByte(c.uppercaseChar().code and 0x1F)
                                    ctrlArmed = false
                                }
                                else -> send(c.toString())
                            }
                        }
                    }
                    new.length < old.length -> repeat(old.length - new.length) { sendByte(0x7F) }
                }
                // 换行不入字段，避免多行累积；字段保持短小
                val cleaned = new.replace("\n", "").replace("\r", "")
                val capped = if (cleaned.length > 200) "" else cleaned
                mirror.value = capped
                input = capped
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .alpha(0f)
                .focusRequester(inputFocus),
            // 不用 ImeAction.Done：那会让回车收起键盘并丢焦点，终端需要连续输入
            singleLine = false,
        )
    }

    // ── 文件服务 ──
    if (showFileService) {
        TianquanFileServiceDialog(onDismiss = { showFileService = false })
    }

    // ── 会话列表 ──
    if (showSessions) {
        AlertDialog(
            onDismissRequest = { showSessions = false },
            title = { Text("终端会话") },
            text = {
                Column(
                    modifier = Modifier.height(300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sessions.isEmpty()) Text("暂无会话", color = StarWhite.copy(alpha = 0.5f))
                    sessions.forEachIndexed { index, s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (index == activeIndex) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    activeIndex = index
                                    showSessions = false
                                    revision++
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.emulator.title.ifBlank { "会话 ${index + 1}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StarWhite,
                                )
                                Text(
                                    if (s.usingPty) "pty · ${s.emulator.buffer.cols}×${s.emulator.buffer.rows}" else "管道模式",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StarWhite.copy(alpha = 0.5f),
                                )
                            }
                            Text(
                                "关闭",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF6B9D),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                                    .clickable { closeSession(s) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startSession()
                    showSessions = false
                }) { Text("新建会话") }
            },
            dismissButton = { TextButton(onClick = { showSessions = false }) { Text("关闭") } },
        )
    }

    // ── 设置 ──
    if (showSettings) {
        TerminalSettingsDialog(
            fontSize = fontSize,
            theme = theme,
            cursorStyle = cursorStyle,
            cursorBlink = cursorBlink,
            shell = TianquanPrefs.shellPath(context),
            scrollback = TianquanPrefs.scrollbackRows(context),
            trueColor = TianquanPrefs.trueColor(context),
            mouseTracking = TianquanPrefs.mouseTrackingEnabled(context),
            ptyAvailable = TianquanPty.available,
            channel = channel,
            channelStatus = TianquanPrivileged.summary(context, channel),
            channelReady = { c -> TianquanPrivileged.ready(context, c) },
            onChannel = {
                channel = it
                TianquanPrefs.setChannel(context, it)
            },
            onFontSize = { fontSize = it; TianquanPrefs.setFontSize(context, it) },
            onTheme = { theme = it; TianquanPrefs.setTheme(context, it.id) },
            onCursorStyle = { cursorStyle = it; TianquanPrefs.setCursorStyle(context, it) },
            onCursorBlink = { cursorBlink = it; TianquanPrefs.setCursorBlinkMs(context, it) },
            onShell = { TianquanPrefs.setShellPath(context, it) },
            onScrollback = { TianquanPrefs.setScrollbackRows(context, it) },
            onTrueColor = { TianquanPrefs.setTrueColor(context, it); active?.emulator?.setTrueColor(it) },
            onMouseTracking = { TianquanPrefs.setMouseTrackingEnabled(context, it) },
            onClearScreen = {
                active?.emulator?.softReset()
                revision++
            },
            onClearScrollback = {
                active?.emulator?.clearScrollback()
                revision++
            },
            onDismiss = { showSettings = false },
        )
    }
}

/** 从缓冲中提取选区文本（按行拼接，去除宽字符占位） */
private fun extractSelection(session: TianquanSession?, sel: TerminalSelection): String {
    val emu = session?.emulator ?: return ""
    val buffer = emu.buffer
    val sb = StringBuilder()
    for (line in sel.startLine..sel.endLine) {
        val row = lineToRow(emu, line) ?: continue
        val from = sel.startColOf(line).coerceIn(0, row.cols)
        val to = sel.endColOf(line).coerceAtMost(row.cols)
        sb.append(row.plainText(from, to))
        if (line != sel.endLine) sb.append('\n')
    }
    return sb.toString().trimEnd()
}

private fun lineToRow(emu: TianquanEmulator, line: Int): TerminalRow? {
    val buffer = emu.buffer
    return if (line < buffer.scrollback.size) {
        buffer.scrollback.elementAtOrNull(line)
    } else {
        buffer.screen.getOrNull(line - buffer.scrollback.size)
    }
}

@Composable
private fun Chip(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
    }
}

@Composable
private fun RowScope.KeyButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
private fun TerminalSettingsDialog(
    fontSize: Int,
    theme: TerminalTheme,
    cursorStyle: CursorStyle,
    cursorBlink: Int,
    shell: String,
    scrollback: Int,
    trueColor: Boolean,
    mouseTracking: Boolean,
    ptyAvailable: Boolean,
    channel: TerminalChannel,
    channelStatus: String,
    channelReady: (TerminalChannel) -> Boolean,
    onChannel: (TerminalChannel) -> Unit,
    onFontSize: (Int) -> Unit,
    onTheme: (TerminalTheme) -> Unit,
    onCursorStyle: (CursorStyle) -> Unit,
    onCursorBlink: (Int) -> Unit,
    onShell: (String) -> Unit,
    onScrollback: (Int) -> Unit,
    onTrueColor: (Boolean) -> Unit,
    onMouseTracking: (Boolean) -> Unit,
    onClearScreen: () -> Unit,
    onClearScrollback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = starAccent(TianquanFeature.star.order)
    var shellText by remember { mutableStateOf(shell) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("终端设置") },
        text = {
            Column(
                modifier = Modifier.height(460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 特权通道 ──
                Text("运行身份（特权通道）", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(
                    "选择终端以何种身份运行。经天枢可取得 ADB shell 权限、经开阳可取得设备所有者权限，指令才能有效执行。",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.6f),
                )
                TerminalChannel.entries.forEach { c ->
                    val ready = if (c == TerminalChannel.AUTO || c == TerminalChannel.LOCAL) true else channelReady(c)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (c == channel) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                            .clickable { onChannel(c) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (c == channel) "◉" else "○", color = if (c == channel) accent else StarWhite.copy(alpha = 0.4f))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = StarWhite)
                            Text(c.detail, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
                        }
                        Text(
                            if (ready) "可用" else "不可用",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ready) accent else StarWhite.copy(alpha = 0.35f),
                        )
                    }
                }
                Text(
                    "当前：$channelStatus（改动后需重启会话生效）",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.75f),
                )

                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                Text("字号 $fontSize sp", style = MaterialTheme.typography.labelMedium, color = StarWhite)
                Slider(value = fontSize.toFloat(), onValueChange = { onFontSize(it.toInt()) }, valueRange = 6f..32f)

                Text("配色", style = MaterialTheme.typography.labelMedium, color = StarWhite)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TerminalThemes.all.forEach { t ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (t.id == theme.id) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                                .clickable { onTheme(t) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (t.id == theme.id) accent else StarWhite.copy(alpha = 0.6f))
                        }
                    }
                }

                Text("光标样式", style = MaterialTheme.typography.labelMedium, color = StarWhite)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CursorStyle.entries.forEach { cs ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (cs == cursorStyle) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                                .clickable { onCursorStyle(cs) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cs.label, style = MaterialTheme.typography.labelSmall, color = if (cs == cursorStyle) accent else StarWhite.copy(alpha = 0.6f))
                        }
                    }
                }

                Text("光标闪烁 ${if (cursorBlink == 0) "关闭" else "${cursorBlink}ms"}", style = MaterialTheme.typography.labelMedium, color = StarWhite)
                Slider(value = cursorBlink.toFloat(), onValueChange = { onCursorBlink(it.toInt()) }, valueRange = 0f..2000f)

                Text("回滚历史 $scrollback 行", style = MaterialTheme.typography.labelMedium, color = StarWhite)
                Slider(value = scrollback.toFloat(), onValueChange = { onScrollback(it.toInt()) }, valueRange = 100f..20000f)

                ToggleLine("真彩（24 位色）", trueColor, accent, onTrueColor)
                ToggleLine("鼠标上报（程序启用时）", mouseTracking, accent, onMouseTracking)

                Text("Shell 路径", style = MaterialTheme.typography.labelMedium, color = StarWhite)
                TextField(
                    value = shellText,
                    onValueChange = { shellText = it; onShell(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("清屏", accent, onClearScreen)
                    Chip("清历史", accent, onClearScrollback)
                }

                Text(
                    if (ptyAvailable) "伪终端：可用（libpty.so）" else "伪终端：不可用（管道模式）",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ptyAvailable) accent else Color(0xFFFF6B9D),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StarWhite, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.35f),
                uncheckedThumbColor = StarWhite.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}
