package org.beidou.stareye.core.shell

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * 经由「天枢中枢」（Shizuku）以 shell / root 身份执行系统命令的共享通道。
 *
 * 不改动 Shizuku 的激活/申请/授权逻辑；[Shizuku.newProcess] 为隐藏方法，
 * 这里以反射调用（与开阳中枢激活同一手法），在 `sh -c` 下执行任意命令。
 *
 * 供各中枢复用：天璇（应用管理）、天玑（安装器）等。
 */
object ShizukuShell {

    /** 天枢是否就绪（服务运行且本应用已获授权） */
    fun ready(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * 以 `sh -c [command]` 执行命令并返回 stdout。
     * 失败时抛异常（含 stderr 或退出码信息）。
     */
    fun run(command: String): Result<String> = runCatching {
        val process = start(command)
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        if (process.exitValue() != 0) {
            error(err.ifBlank { out }.trim().ifBlank { "命令退出码 ${process.exitValue()}" })
        }
        out
    }

    /**
     * 以 `sh -c [command]` 执行命令并返回 stdout 的原始字节（用于导出 APK 等二进制）。
     */
    fun runBytes(command: String): Result<ByteArray> = runCatching {
        val process = start(command)
        val bytes = process.inputStream.readBytes()
        process.waitFor()
        if (process.exitValue() != 0) {
            error("命令退出码 ${process.exitValue()}")
        }
        bytes
    }

    /**
     * 启动远程进程并返回句柄，调用方可自行读写其 stdin / stdout
     * （用于需要把大文件流式喂给 shell 的场景，如安装 APK）。
     *
     * 调用方负责完整读取输出并 [ShizukuRemoteProcess.waitFor]。
     */
    fun start(command: String): ShizukuRemoteProcess =
        newProcess(arrayOf("sh", "-c", command))

    /** 反射调用 Shizuku 的私有 newProcess（以 shell 身份启动远程进程） */
    private fun newProcess(cmd: Array<String>): ShizukuRemoteProcess {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as ShizukuRemoteProcess
    }
}
