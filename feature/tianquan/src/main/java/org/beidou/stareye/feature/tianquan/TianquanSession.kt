package org.beidou.stareye.feature.tianquan

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 天权中枢 —— 终端会话（自研实现）。
 *
 * 三种启动方式，对外统一暴露为「一对输入输出流 + 终端模拟器」：
 *  · **本地 pty**：[TianquanPty] 在本进程 fork，应用自身权限，完整 pty 语义
 *  · **特权 pty**：[TianquanPrivileged] 经天枢 / 开阳 / Root 启动中继可执行文件，
 *    中继在特权进程内再开 pty —— 既提权又保留完整 pty 语义
 *  · **管道回退**：原生库不可用时的兜底（无 pty 语义）
 */
class TianquanSession(
    private val context: Context,
    private val shellPath: String,
    rows: Int,
    cols: Int,
    palette: TerminalPalette,
    val channel: TerminalChannel = TerminalChannel.LOCAL,
    scrollbackRows: Int = 2000,
    trueColor: Boolean = true,
    /** 屏幕内容有更新时回调（读线程调用，UI 侧需自行切线程） */
    private val onUpdate: () -> Unit,
    /** 会话结束回调，参数为退出码描述 */
    private val onExit: (String) -> Unit = {},
    /** OSC 0/1/2 标题变化 */
    private val onTitle: (String) -> Unit = {},
    /** OSC 52 写剪贴板 */
    private val onClipboard: (String) -> Unit = {},
    /** BEL */
    private val onBell: () -> Unit = {},
    /** 程序请求改变尺寸（如 DECCOLM 132 列） */
    private val onResizeRequest: (Int, Int) -> Unit = { _, _ -> },
) {

    val emulator = TianquanEmulator(
        rows = rows,
        cols = cols,
        palette = palette,
        scrollbackRows = scrollbackRows,
        onResponse = { bytes -> write(bytes) },
        onTitle = onTitle,
        onClipboard = onClipboard,
        onBell = onBell,
        onResizeRequest = onResizeRequest,
    ).apply { setTrueColor(trueColor) }

    private var pfd: ParcelFileDescriptor? = null
    private var pid: Int = -1
    private var masterFd: Int = -1
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var process: Process? = null
    private var reader: Thread? = null
    private val closed = AtomicBoolean(false)

    /** 特权模式下用于下发窗口尺寸的文件（中继预创建为 0666） */
    private var sizeFile: String? = null

    /** 实际生效的身份描述 */
    var modeLabel: String = "本地"
        private set

    var started: Boolean = false
        private set

    val usingPty: Boolean get() = masterFd >= 0 || process != null

    val isAlive: Boolean
        get() = if (closed.get()) {
            false
        } else {
            if (process != null) processAlive() else masterFd >= 0
        }

    /**
     * 远程进程是否仍在运行。
     *
     * 不能直接用 [Process.isAlive]：标准实现靠捕获 `IllegalThreadStateException`
     * 判断"未退出"，而 Shizuku 的远程 Process 在未退出时抛出的是
     * `IllegalArgumentException("process hasn't exited")`（经 binder 回传）——
     * 该异常会穿透 isAlive 并在 Compose 组合期间崩溃（真机实测：浮动窗）。
     * 这里直接调 [Process.exitValue] 并按两种异常语义分别判定。
     */
    private fun processAlive(): Boolean = try {
        process?.exitValue()
        false // 能取到退出码 = 已退出
    } catch (_: IllegalThreadStateException) {
        true // 标准语义：进程仍在运行
    } catch (e: IllegalArgumentException) {
        e.message?.contains("hasn't exited") == true
    } catch (_: Throwable) {
        false
    }

    /** 启动 shell 会话，须在工作线程调用 */
    fun start(): Result<Unit> = runCatching {
        val resolved = TianquanPrivileged.resolve(context, channel)
        when {
            resolved != TerminalChannel.LOCAL && TianquanPrivileged.relayAvailable(context) ->
                startPrivileged(resolved)
            TianquanPty.available -> startLocalPty()
            else -> startPipeFallback()
        }
    }

    // ───────────────────────── 本地 pty ─────────────────────────

    private fun startLocalPty() {
        val home = File(context.filesDir, "home").apply { mkdirs() }
        val rc = File(home, RC_NAME).apply { writeText(RC_SCRIPT) }

        val out = IntArray(1)
        val fd = TianquanPty.open(
            shellPath,
            localEnv(home, rc),
            home.absolutePath,
            emulator.buffer.rows,
            emulator.buffer.cols,
            out,
        )
        check(fd >= 0) { "无法分配伪终端（shell=$shellPath）" }

        masterFd = fd
        pid = out[0]
        val descriptor = ParcelFileDescriptor.adoptFd(fd)
        pfd = descriptor
        input = FileInputStream(descriptor.fileDescriptor)
        output = FileOutputStream(descriptor.fileDescriptor)
        modeLabel = "本地（应用权限）"
        started = true
        startReader()
    }

    // ───────────────────────── 特权中继 ─────────────────────────

    private fun startPrivileged(resolved: TerminalChannel) {
        val sizePath = "$PRIVILEGED_HOME/.beidou_size"
        sizeFile = sizePath

        val proc = TianquanPrivileged.start(
            context = context,
            channel = resolved,
            shell = shellPath,
            rows = emulator.buffer.rows,
            cols = emulator.buffer.cols,
            sizeFile = sizePath,
            rcScript = RC_SCRIPT,
            extraEnv = privilegedEnv(),
        )
        process = proc
        input = proc.inputStream
        output = proc.outputStream
        modeLabel = "${resolved.label} · 已提权"
        started = true
        startReader()
    }

    /** 无原生库时的回退：普通管道（无回显与彩色） */
    private fun startPipeFallback() {
        val home = File(context.filesDir, "home").apply { mkdirs() }
        val rc = File(home, RC_NAME).apply { writeText(RC_SCRIPT) }
        val pb = ProcessBuilder(shellPath, "-i")
        pb.directory(home)
        localEnv(home, rc).forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) pb.environment()[kv.substring(0, i)] = kv.substring(i + 1)
        }
        pb.redirectErrorStream(true)
        val proc = pb.start()
        process = proc
        input = proc.inputStream
        output = proc.outputStream
        modeLabel = "本地 · 管道模式"
        started = true
        startReader()
    }

    // ───────────────────────── 公共部分 ─────────────────────────

    private fun startReader() {
        val stream = input ?: return
        reader = thread(name = "tianquan-reader", isDaemon = true) {
            val buf = ByteArray(16384)
            try {
                while (!closed.get()) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    emulator.feed(buf, n)
                    onUpdate()
                }
            } catch (_: Throwable) {
                // 会话结束或流被关闭
            }
            if (!closed.get()) {
                val code = exitCode()
                onExit(if (code >= 0) "会话已结束（退出码 $code）" else "会话已结束")
            }
        }
    }

    private fun exitCode(): Int = when {
        process != null -> runCatching { process!!.exitValue() }.getOrDefault(-1)
        masterFd >= 0 && pid > 0 -> TianquanPty.pollExit(pid)
        else -> -1
    }

    /** 向终端写入（键盘输入 / 应答序列） */
    fun write(data: ByteArray) {
        if (closed.get()) return
        try {
            output?.write(data)
            output?.flush()
        } catch (_: Throwable) {
            // 会话已结束
        }
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    /** 终端尺寸变化 */
    fun resize(rows: Int, cols: Int) {
        if (closed.get()) return
        if (rows == emulator.buffer.rows && cols == emulator.buffer.cols) return
        emulator.resize(rows, cols)
        when {
            masterFd >= 0 -> runCatching { TianquanPty.setSize(masterFd, rows, cols) }
            sizeFile != null -> TianquanPrivileged.pushSize(sizeFile!!, rows, cols)
        }
        onUpdate()
    }

    /** 结束会话并释放资源 */
    fun destroy() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { output?.close() }
        runCatching { input?.close() }
        runCatching { pfd?.close() }
        runCatching { process?.destroy() }
        masterFd = -1
        reader = null
    }

    // ───────────────────────── 环境与初始化脚本 ─────────────────────────

    private fun localEnv(home: File, rc: File): Array<String> {
        val tmp = File(context.cacheDir, "tmp").apply { mkdirs() }
        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=en_US.UTF-8",
            "LC_ALL=en_US.UTF-8",
            "HOME=${home.absolutePath}",
            "TMPDIR=${tmp.absolutePath}",
            "PWD=${home.absolutePath}",
            "PATH=${home.absolutePath}/.local/bin:/system/bin:/system/xbin:/vendor/bin",
            "SHELL=$shellPath",
            "ENV=${rc.absolutePath}",
            // 让多数程序在非登录环境下也输出颜色
            "CLICOLOR_FORCE=1",
            "FORCE_COLOR=1",
            "CLICOLOR=1",
        )
    }

    private fun privilegedEnv(): Array<String> = arrayOf(
        "TERM=xterm-256color",
        "COLORTERM=truecolor",
        "LANG=en_US.UTF-8",
        "LC_ALL=en_US.UTF-8",
        "PATH=$PRIVILEGED_HOME/.local/bin:/system/bin:/system/xbin:/vendor/bin",
        "SHELL=$shellPath",
        "CLICOLOR_FORCE=1",
        "FORCE_COLOR=1",
        "CLICOLOR=1",
    )

    companion object {
        private const val RC_NAME = ".beidou_rc"
        private const val PRIVILEGED_HOME = TianquanPrivileged.PRIVILEGED_HOME

        /**
         * 终端初始化脚本：注入到 shell 的 ENV。
         *
         * 关键点是 `PS1='$(__beidou_pwd) $ '` —— mksh 的 PS1 支持命令替换
         * （Android 自带的 `/system/etc/mkshrc` 正是这么写的），
         * 于是能把家目录缩写成 `~`，避免提示符里出现一长串应用私有路径。
         */
        val RC_SCRIPT: String = """
            # 北斗·群星之眼 · 终端初始化
            [ -f /system/etc/mkshrc ] && . /system/etc/mkshrc 2>/dev/null

            __beidou_pwd() {
              case "${'$'}PWD" in
                "${'$'}HOME") printf '~' ;;
                "${'$'}HOME"/*) printf '~%s' "${'$'}{PWD#${'$'}HOME}" ;;
                *) printf '%s' "${'$'}PWD" ;;
              esac
            }
            PS1='${'$'}(__beidou_pwd) ${'$'} '

            alias ll='ls -alF'
            alias la='ls -A'
            alias l='ls -CF'
            alias ..='cd ..'
            alias ...='cd ../..'
        """.trimIndent() + "\n"
    }
}
