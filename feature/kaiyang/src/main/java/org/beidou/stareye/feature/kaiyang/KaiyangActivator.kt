package org.beidou.stareye.feature.kaiyang

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * 通过「天枢中枢」（Shizuku）以 shell 身份激活开阳中枢为设备所有者。
 *
 * 原理：天枢的 server 以 shell(uid 2000) 身份运行，具备执行 `dpm` 的权限。
 * 这里借它执行 `dpm set-device-owner`，实现全程无需电脑的应用内激活。
 */
object KaiyangActivator {

    /** 天枢是否就绪（服务运行且已授权） */
    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * 经由天枢执行激活。阻塞调用，须在工作线程执行。
     *
     * @return 是否成功成为设备所有者
     */
    fun activateViaShizuku(context: Context, onOutput: (String) -> Unit): Boolean {
        if (!shizukuReady()) {
            onOutput("天枢中枢未就绪：请先在「天枢中枢」中激活服务并授权。")
            return false
        }

        val component = "${context.packageName}/${KaiyangDAReceiver::class.java.name}"
        // stderr 合并进 stdout，避免双流阻塞
        val command = "dpm set-device-owner $component 2>&1"

        return try {
            val process = newShizukuProcess(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            onOutput(output.trim())
            KaiyangState.isOwner(context)
        } catch (e: Throwable) {
            onOutput("激活失败：${e.message}")
            false
        }
    }

    /**
     * 调用 Shizuku 的私有 newProcess（以 shell 身份启动进程）。
     * 通过反射调用，不改动 Shizuku 自身逻辑。
     */
    private fun newShizukuProcess(cmd: Array<String>): ShizukuRemoteProcess {
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
