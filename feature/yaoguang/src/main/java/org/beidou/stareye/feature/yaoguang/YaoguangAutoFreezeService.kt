package org.beidou.stareye.feature.yaoguang

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import org.beidou.stareye.core.freeze.FreezeState

/**
 * 自动冻结服务。
 *
 * 常驻前台并动态监听息屏 / 亮屏：息屏后按设定延迟冻结清单内应用，
 * 亮屏则解冻「本批自动冻结」的那些（用户手动冻结的不受影响）。
 *
 * 延迟触发走「双路径」：服务存活时用 Handler 精确定时；同时向
 * AlarmManager 排一个兜底闹钟，进程被系统回收后仍会拉起服务执行
 * （闹钟触发的 [ACTION_FREEZE_NOW] 会校验计划是否已由快速路径完成）。
 *
 * 同时继承通知监听服务，为「跳过有通知的应用」提供数据
 * —— 该能力需用户在系统设置中授予「通知使用权」，未授予时自动跳过该条件。
 */
class YaoguangAutoFreezeService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val pendingFreeze = Runnable { runFreeze() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!YaoguangStore.autoFreeze(context)) return
                    val delay = YaoguangStore.autoFreezeDelay(context) * 60_000L
                    val at = System.currentTimeMillis() + delay
                    YaoguangStore.setPendingAutoFreezeAt(context, at)
                    // 快速路径：进程存活时按精确时刻执行
                    handler.postDelayed(pendingFreeze, delay)
                    // 兜底路径：进程被杀后由闹钟拉起服务执行
                    armAlarm(context, at)
                }

                Intent.ACTION_SCREEN_ON -> {
                    handler.removeCallbacks(pendingFreeze)
                    YaoguangStore.setPendingAutoFreezeAt(context, 0L)
                    cancelAlarm(context)
                    runUnfreeze()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action == ACTION_FREEZE_NOW) {
            // 兜底闹钟到期：若快速路径已执行（计划时刻已被清空）则跳过，避免重复冻结
            val planned = YaoguangStore.pendingAutoFreezeAt(this)
            if (planned != 0L && planned <= System.currentTimeMillis() + 5_000L) {
                handler.removeCallbacks(pendingFreeze)
                runFreeze()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pendingFreeze)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    // ── 冻结 / 解冻 ──

    private fun runFreeze() {
        val context = applicationContext
        // 无论结果如何，本次计划均已消费（兜底闹钟据此判断是否跳过）
        YaoguangStore.setPendingAutoFreezeAt(context, 0L)
        if (YaoguangStore.skipWhileCharging(context) && YaoguangOps.isCharging(context)) {
            YaoguangStore.appendLog(context, "自动冻结", "已跳过", "正在充电")
            return
        }

        var targets = YaoguangStore.items(context).filterNot { it.whitelisted }.map { it.packageName }
        val skipped = mutableListOf<String>()
        if (YaoguangStore.skipForegroundApp(context)) {
            val recent = YaoguangOps.recentlyUsed(context, YaoguangStore.autoFreezeDelay(context) + 1)
            val hit = targets.count { it in recent }
            if (hit > 0) skipped += "使用中 $hit 个"
            targets = targets.filterNot { it in recent }
        }
        if (YaoguangStore.skipNotifyingApp(context)) {
            val notifying = notifyingPackages()
            val hit = targets.count { it in notifying }
            if (hit > 0) skipped += "有通知 $hit 个"
            targets = targets.filterNot { it in notifying }
        }
        if (targets.isEmpty()) {
            YaoguangStore.appendLog(
                context, "自动冻结", "无可冻结应用",
                skipped.joinToString("；").ifBlank { "清单为空或均为白名单" },
            )
            return
        }

        val result = YaoguangOps.setFrozenBatch(context, targets, true)
        // 记录本批真正冻结成功的应用，供亮屏时精确解冻
        val frozenNow = targets.filter { FreezeState.isFrozen(context, it) }
        YaoguangStore.setAutoFrozen(context, YaoguangStore.autoFrozen(context) + frozenNow)
        val note = if (skipped.isEmpty()) result.text else "${result.text}（跳过 ${skipped.joinToString("；")}）"
        YaoguangStore.appendLog(context, "自动冻结", "${targets.size} 个应用", note)
    }

    private fun runUnfreeze() {
        val context = applicationContext
        val recorded = YaoguangStore.autoFrozen(context)
        if (recorded.isEmpty()) return

        val targets = recorded.filter { FreezeState.isFrozen(context, it) }
        if (targets.isNotEmpty()) {
            val result = YaoguangOps.setFrozenBatch(context, targets, false)
            YaoguangStore.appendLog(context, "自动解冻", "${targets.size} 个应用", result.text)
        }
        YaoguangStore.setAutoFrozen(context, emptyList())
    }

    private fun notifyingPackages(): Set<String> = try {
        activeNotifications?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (_: Throwable) {
        emptySet()
    }

    // ── 前台通知 ──

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "自动冻结", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "息屏后自动冻结清单内应用"
                }
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("瑶光 · 自动冻结")
            .setContentText("息屏后自动冻结清单内应用，亮屏时解冻")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "yaoguang_auto_freeze"
        private const val NOTIFICATION_ID = 1001

        /** 兜底闹钟拉起服务时携带的动作（内部使用） */
        private const val ACTION_FREEZE_NOW =
            "org.beidou.stareye.feature.yaoguang.action.FREEZE_NOW"
        private const val REQUEST_FREEZE = 1002

        /** 开启 / 关闭自动冻结（由界面或磁贴调用） */
        fun setEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, YaoguangAutoFreezeService::class.java)
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
                // 关闭时一并撤销兜底闹钟与待执行计划
                cancelAlarm(context)
                YaoguangStore.setPendingAutoFreezeAt(context, 0L)
            }
        }

        private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getForegroundService(
            context,
            REQUEST_FREEZE,
            Intent(context, YaoguangAutoFreezeService::class.java).setAction(ACTION_FREEZE_NOW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun armAlarm(context: Context, at: Long) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context))
        }

        private fun cancelAlarm(context: Context) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.cancel(alarmIntent(context))
        }
    }
}
