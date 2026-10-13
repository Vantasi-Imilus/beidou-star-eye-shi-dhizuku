package org.beidou.stareye.feature.yaoguang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 对外动作的广播入口（自有 action）。
 *
 * 广播无界面，适合 adb / 自动化工具调用；批量动作可能耗时，
 * 故以 goAsync 转后台线程执行，避免阻塞主线程触发 ANR。
 */
class YaoguangApiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val actionName = intent.action?.substringAfterLast('.') ?: "?"
        val target = intent.getStringExtra(YaoguangApi.EXTRA_PACKAGE).orEmpty()
        Thread {
            try {
                val result = YaoguangApi.dispatch(appContext, intent)
                YaoguangStore.appendLog(appContext, "外部调用·$actionName", target, result.message)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
