package org.beidou.stareye.feature.tianquan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.File
import android.app.Notification as AndroidNotification

/**
 * 天权中枢 —— 局域网文件服务的单例持有者。
 *
 * 与服务绑定的生命周期解耦：UI 只调用这里的启停方法，
 * 由 [TianquanFileService] 负责以前台服务形式保活。
 */
object TianquanFileServers {

    var http: TianquanHttpServer? = null
        private set
    var ftp: TianquanFtpServer? = null
        private set

    val httpRunning: Boolean get() = http?.isRunning == true
    val ftpRunning: Boolean get() = ftp?.isRunning == true

    /** 默认共享目录：终端的工作目录（应用私有 home） */
    fun defaultRoot(context: Context): File =
        File(context.filesDir, "home").apply { mkdirs() }

    fun shareRoot(context: Context): File = File(
        context.getSharedPreferences("tianquan_prefs", Context.MODE_PRIVATE)
            .getString("share_root", null) ?: defaultRoot(context).absolutePath,
    ).let { if (it.isDirectory) it else defaultRoot(context) }

    fun setShareRoot(context: Context, dir: File) {
        context.getSharedPreferences("tianquan_prefs", Context.MODE_PRIVATE)
            .edit().putString("share_root", dir.absolutePath).apply()
    }

    @Synchronized
    fun startHttp(context: Context): Result<String> {
        stopHttp()
        val server = TianquanHttpServer(rootProvider = { shareRoot(context) })
        return server.start().map { url ->
            http = server
            syncService(context)
            url
        }
    }

    @Synchronized
    fun stopHttp() {
        http?.stop()
        http = null
    }

    @Synchronized
    fun startFtp(context: Context): Result<String> {
        stopFtp()
        val server = TianquanFtpServer(rootProvider = { shareRoot(context) })
        return server.start().map { url ->
            ftp = server
            syncService(context)
            url
        }
    }

    @Synchronized
    fun stopFtp() {
        ftp?.stop()
        ftp = null
    }

    @Synchronized
    fun stopAll(context: Context?) {
        stopHttp()
        stopFtp()
        context?.let { syncService(it) }
    }

    /** 有服务在跑就起前台服务，否则停掉 */
    private fun syncService(context: Context) {
        val intent = Intent(context, TianquanFileService::class.java)
        if (httpRunning || ftpRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            TianquanFileService.updateNotification(context)
        } else {
            context.stopService(intent)
        }
    }
}

/**
 * 前台服务：让局域网文件服务在应用退到后台后继续可用。
 */
class TianquanFileService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForeground(NOTIFICATION_ID, buildNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(this))
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { TianquanFileServers.stopAll(null) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tianquan_file_service"
        private const val NOTIFICATION_ID = 0x7A01

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "文件服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "局域网 HTTP / FTP 文件服务运行中" }
            manager.createNotificationChannel(channel)
        }

        fun buildNotification(context: Context): Notification {
            val lines = buildList {
                TianquanFileServers.http?.takeIf { it.isRunning }?.let { add("HTTP ${it.url()}") }
                TianquanFileServers.ftp?.takeIf { it.isRunning }?.let { add("FTP ${it.url()}") }
            }
            val text = lines.joinToString(" · ").ifBlank { "服务未运行" }

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pending = launch?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AndroidNotification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                AndroidNotification.Builder(context)
            }
            return builder
                .setContentTitle("北斗·群星之眼 · 文件服务")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .apply { pending?.let { setContentIntent(it) } }
                .build()
        }

        fun updateNotification(context: Context) {
            runCatching {
                ensureChannel(context)
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                manager.notify(NOTIFICATION_ID, buildNotification(context))
            }
        }
    }
}
