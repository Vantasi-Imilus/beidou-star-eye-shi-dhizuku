package org.beidou.stareye.feature.tianji

import android.content.Context
import com.rosan.dhizuku.api.Dhizuku
import org.beidou.stareye.core.shell.ShizukuShell
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.File

/**
 * 特权通道（授权器）—— 天玑中枢可选的三种执行后端。
 */
enum class Authorizer(val label: String, val detail: String) {
    /** 自动：按 天枢 → 开阳 → Root 顺序选用首个可用者 */
    AUTO("自动", "按 天枢 → 开阳 → Root 顺序自动选用"),
    SHIZUKU("天枢（Shizuku）", "以 ADB / Root 身份运行 Shizuku 服务"),
    DHIZUKU("开阳（Dhizuku）", "以设备所有者身份执行特权操作"),
    ROOT("Root（su）", "直接调用本机 su 提权"),
}

/**
 * 统一特权执行通道：Shizuku / Dhizuku / Root 三者都归一到 [java.lang.Process]，
 * 使安装引擎无需感知后端差异（三者均支持 stdin/stdout 流式读写）。
 */
object TianjiPrivileged {

    /** 该授权器当前是否可用 */
    fun ready(context: Context, authorizer: Authorizer): Boolean = when (authorizer) {
        Authorizer.AUTO -> Authorizer.entries.any { it != Authorizer.AUTO && ready(context, it) }
        Authorizer.SHIZUKU -> ShizukuShell.ready()
        Authorizer.DHIZUKU -> dhizukuReady(context)
        Authorizer.ROOT -> rootReady()
    }

    /** 实际选用的授权器（AUTO 时解析为具体后端） */
    fun resolve(context: Context, authorizer: Authorizer): Authorizer? = when (authorizer) {
        Authorizer.AUTO ->
            listOf(Authorizer.SHIZUKU, Authorizer.DHIZUKU, Authorizer.ROOT)
                .firstOrNull { ready(context, it) }
        else -> authorizer.takeIf { ready(context, it) }
    }

    fun describe(context: Context, authorizer: Authorizer): String =
        resolve(context, authorizer)?.label ?: "不可用"

    private fun dhizukuReady(context: Context): Boolean = try {
        val ok = Dhizuku.init(context)
        ok && Dhizuku.isPermissionGranted()
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

    /**
     * 以 `sh -c [command]` 启动远程进程，调用方可自行读写 stdin/stdout。
     */
    fun start(context: Context, authorizer: Authorizer, command: String): Process {
        val resolved = resolve(context, authorizer) ?: error("无可用特权通道（需天枢 / 开阳 / Root 之一）")
        return when (resolved) {
            Authorizer.SHIZUKU -> shizukuProcess(arrayOf("sh", "-c", command))
            Authorizer.DHIZUKU -> dhizukuProcess(context, arrayOf("sh", "-c", command))
            Authorizer.ROOT -> Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            Authorizer.AUTO -> error("unreachable")
        }
    }

    fun run(context: Context, authorizer: Authorizer, command: String): Result<String> = runCatching {
        val process = start(context, authorizer, command)
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        if (process.exitValue() != 0) {
            error(err.ifBlank { out }.trim().ifBlank { "命令退出码 ${process.exitValue()}" })
        }
        out
    }

    // ── 各后端进程构造 ──

    /** Shizuku 的 newProcess 为私有方法，反射调用（与既有中枢同一手法） */
    private fun shizukuProcess(cmd: Array<String>): ShizukuRemoteProcess {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as ShizukuRemoteProcess
    }

    private fun dhizukuProcess(context: Context, cmd: Array<String>): Process {
        Dhizuku.init(context)
        check(Dhizuku.isPermissionGranted()) { "开阳中枢未授权" }
        return Dhizuku.newProcess(cmd, null, File("/")) ?: error("开阳中枢启动进程失败")
    }
}
