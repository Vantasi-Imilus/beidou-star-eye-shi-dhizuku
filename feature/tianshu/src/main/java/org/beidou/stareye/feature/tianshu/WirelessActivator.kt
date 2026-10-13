package org.beidou.stareye.feature.tianshu

import android.content.Context
import org.beidou.stareye.feature.tianshu.adb.AdbClient
import org.beidou.stareye.feature.tianshu.adb.AdbKey
import org.beidou.stareye.feature.tianshu.adb.PreferenceAdbKeyStore

/**
 * 无线调试激活器：直接通过已检测到的无线调试端口，在本应用内完成天枢激活。
 *
 * 复用原版 Shizuku 的 ADB 协议客户端（AdbClient/AdbKey）。若本应用的调试密钥
 * 尚未被设备授权，系统会弹出「允许 USB 调试」对话框，用户同意后即完成授权与激活。
 */
object WirelessActivator {

    private const val PREFS = "tianshu_adb"
    private const val KEY_NAME = "beidou"

    /**
     * 连接 [host]:[port] 并执行 [scriptPath] 完成激活。
     * 阻塞调用，须在工作线程执行。输出通过 [onOutput] 回调。
     */
    fun activate(
        context: Context,
        host: String,
        port: Int,
        scriptPath: String,
        onOutput: (String) -> Unit,
    ): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = AdbKey(PreferenceAdbKeyStore(prefs), KEY_NAME)
            AdbClient(host, port, key).use { client ->
                client.connect()
                onOutput("已连接 adb，正在激活…")
                client.shellCommand("sh $scriptPath") { bytes ->
                    onOutput(String(bytes))
                }
            }
            true
        } catch (e: Throwable) {
            onOutput("激活失败：${e.message}")
            false
        }
    }
}
