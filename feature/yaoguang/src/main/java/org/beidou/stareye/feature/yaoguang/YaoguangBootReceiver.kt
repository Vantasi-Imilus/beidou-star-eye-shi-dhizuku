package org.beidou.stareye.feature.yaoguang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启：设备重启后恢复自动冻结的常驻服务，并重排延时冻结闹钟。
 *
 * 系统在开机完成后发送 [Intent.ACTION_BOOT_COMPLETED]，此时拉起前台服务
 * 属于后台启动限制的豁免场景。注意华为等 ROM 还需用户在系统
 * 「应用启动管理」中允许自启动，否则本接收器收不到广播。
 */
class YaoguangBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext

        if (YaoguangStore.autoFreeze(app)) {
            YaoguangAutoFreezeService.setEnabled(app, true)
            YaoguangStore.appendLog(app, "开机自启", "自动冻结", "常驻服务已恢复")
        }
        val pending = YaoguangDelayedFreeze.pending(app)
        if (pending.isNotEmpty()) {
            YaoguangDelayedFreeze.reschedule(app)
            YaoguangStore.appendLog(app, "开机自启", "延时冻结", "已重排 ${pending.size} 个计划")
        }
    }
}
