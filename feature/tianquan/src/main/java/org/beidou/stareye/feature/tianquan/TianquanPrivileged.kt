package org.beidou.stareye.feature.tianquan

import android.content.Context
import android.content.pm.PackageManager
import com.rosan.dhizuku.api.Dhizuku
import org.beidou.stareye.core.shell.ShizukuShell
import rikka.shizuku.Shizuku
import java.io.File

/**
 * 天权中枢 —— 终端特权通道。
 *
 * 终端会话可运行在四种身份下：
 *  · [LOCAL]   应用自身权限（原生 pty，本机 fork）
 *  · [SHIZUKU] 天枢中枢（Shizuku）→ 以 ADB shell / root 身份
 *  · [DHIZUKU] 开阳中枢（Dhizuku）→ 以设备所有者身份
 *  · [ROOT]    直接 su 提权
 *
 * 特权模式下由原生可执行文件 [RELAY_NAME] 在特权进程中再分配 pty 并跑 shell，
 * 使宿主应用在网络/权限受限的环境里也能拿到**完整 pty 语义的特权交互终端**。
 */
enum class TerminalChannel(val label: String, val detail: String) {
    LOCAL("本地", "以应用自身权限运行（原生 pty）"),
    AUTO("自动", "按 天枢 → 开阳 → Root 依次择优"),
    SHIZUKU("天枢（ADB）", "经天枢中枢以 shell 身份运行"),
    DHIZUKU("开阳（Dhizuku）", "经开阳中枢以设备所有者身份运行"),
    ROOT("Root", "经 su 提权运行"),
}

object TianquanPrivileged {

    /** 特权中继可执行文件（app 模块 CMake 产出，随 APK 以 legacy 方式解压到 lib 目录） */
    const val RELAY_NAME = "libtianquanpty.so"

    /** 特权模式下 shell 的家目录（shell 身份可写） */
    const val PRIVILEGED_HOME = "/data/local/tmp"

    fun relayPath(context: Context): String =
        File(context.applicationInfo.nativeLibraryDir, RELAY_NAME).absolutePath

    fun relayAvailable(context: Context): Boolean = File(relayPath(context)).canExecute()

    // ── 可用性探测 ──

    fun ready(context: Context, channel: TerminalChannel): Boolean = when (channel) {
        TerminalChannel.LOCAL -> true
        TerminalChannel.AUTO -> ORDER.any { ready(context, it) }
        TerminalChannel.SHIZUKU -> ShizukuShell.ready()
        TerminalChannel.DHIZUKU -> dhizukuReady(context)
        TerminalChannel.ROOT -> rootReady()
    }

    /** AUTO 解析为具体通道；无线程可用时返回 [TerminalChannel.LOCAL] */
    fun resolve(context: Context, channel: TerminalChannel): TerminalChannel = when (channel) {
        TerminalChannel.AUTO -> ORDER.firstOrNull { ready(context, it) } ?: TerminalChannel.LOCAL
        else -> if (ready(context, channel)) channel else TerminalChannel.LOCAL
    }

    fun describe(context: Context, channel: TerminalChannel): String =
        resolve(context, channel).label

    private val ORDER = listOf(TerminalChannel.SHIZUKU, TerminalChannel.DHIZUKU, TerminalChannel.ROOT)

    private fun dhizukuReady(context: Context): Boolean = try {
        Dhizuku.init(context) && Dhizuku.isPermissionGranted()
    } catch (_: Throwable) {
        false
    }

    private fun rootReady(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        p.exitValue() == 0 && out.contains("uid=0")
    } catch (_: Throwable) {
        false
    }

    // ── 启动特权中继 ──

    /**
     * 以特权身份启动中继进程。返回的 [Process] 的 stdin/stdout 即终端数据流。
     *
     * @param sizeFile 尺寸下发文件（中继会预创建并放开写权限）
     * @param rcScript 初始化脚本内容（中继写入 `$HOME/.beidou_rc` 并以 ENV 指向）
     */
    fun start(
        context: Context,
        channel: TerminalChannel,
        shell: String,
        rows: Int,
        cols: Int,
        sizeFile: String,
        rcScript: String,
        extraEnv: Array<String>,
    ): Process {
        val resolved = resolve(context, channel)
        require(resolved != TerminalChannel.LOCAL) { "该通道不可用，请改用本地模式" }
        val relay = relayPath(context)
        check(File(relay).canExecute()) { "特权中继不可执行：$relay" }

        val argv = arrayOf(relay, shell, rows.toString(), cols.toString(), sizeFile)
        val env = extraEnv + arrayOf(
            "HOME=$PRIVILEGED_HOME",
            "TMPDIR=$PRIVILEGED_HOME",
            "BEIDOU_RC=$rcScript",
            "BEIDOU_RC_FILE=$PRIVILEGED_HOME/.beidou_rc",
        )

        return when (resolved) {
            TerminalChannel.SHIZUKU -> shizukuProcess(argv, env, PRIVILEGED_HOME)
            TerminalChannel.DHIZUKU -> dhizukuProcess(context, argv, env)
            TerminalChannel.ROOT -> {
                val quoted = argv.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
                Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "exec $quoted"),
                    env,
                    File(PRIVILEGED_HOME).takeIf { it.isDirectory },
                )
            }
            else -> error("unreachable")
        }
    }

    /** 下发新的窗口尺寸（特权模式下 pty 在远端进程里，管道无法传 SIGWINCH） */
    fun pushSize(sizeFile: String, rows: Int, cols: Int) {
        runCatching { File(sizeFile).writeText("$rows $cols\n") }
    }

    /** Shizuku 的 newProcess 为私有方法，反射调用（与既有中枢同一手法） */
    private fun shizukuProcess(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?,
    ): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, env, dir) as Process
    }

    private fun dhizukuProcess(
        context: Context,
        cmd: Array<String>,
        env: Array<String>,
    ): Process {
        Dhizuku.init(context)
        check(Dhizuku.isPermissionGranted()) { "开阳中枢未授权" }
        // Dhizuku 的 newProcess 签名带 env 与工作目录（公开 API）
        return Dhizuku.newProcess(cmd, env, File(PRIVILEGED_HOME))
            ?: error("开阳中枢启动进程失败")
    }

    /** 供 UI 显示的当前通道状态摘要 */
    fun summary(context: Context, channel: TerminalChannel): String {
        val resolved = resolve(context, channel)
        return if (resolved == TerminalChannel.LOCAL) {
            "本地（应用权限）"
        } else {
            "${resolved.label} · 已提权"
        }
    }

    internal fun hasShizukuPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }
}
