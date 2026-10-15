package org.beidou.stareye.feature.tianquan

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * 天权中枢 —— 对外集成入口（Tasker / 快捷方式等）。
 *
 * 契约动作以 applicationId（`org.beidou.star.eye.furry.vantasiimilus.god.api`）为前缀，
 * 由透明入口 Activity [TianquanExternalRelayActivity] 承接后转给浮动终端前台服务：
 *  · [NEW_SESSION]：新建一个浮动会话（强制新会话，不复用）；
 *  · [RUN_COMMAND]：在浮动会话中执行一行命令（extra [EXTRA_COMMAND]；无会话则新建）；
 *  · [FLOATING_WINDOW]：打开浮动窗（复用已有会话，无则新建）。
 *
 * 需要悬浮窗权限（未授权时自动跳转系统设置引导）。
 */
object TianquanActions {

    /** 新建会话 */
    const val NEW_SESSION = "org.beidou.star.eye.furry.vantasiimilus.god.api.action.NEW_SESSION"

    /** 执行一行命令（extra：command） */
    const val RUN_COMMAND = "org.beidou.star.eye.furry.vantasiimilus.god.api.action.RUN_COMMAND"

    /** 打开浮动窗 */
    const val FLOATING_WINDOW = "org.beidou.star.eye.furry.vantasiimilus.god.api.action.FLOATING_WINDOW"

    /** [RUN_COMMAND] 的命令 extra 键 */
    const val EXTRA_COMMAND = "command"
}

/**
 * 透明入口 Activity：承接外部 action 后立刻转发并结束自身。
 * 无界面（Theme.Translucent），对外集成者（Tasker 等）用 Activity 意图即可触发。
 */
class TianquanExternalRelayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            TianquanActions.NEW_SESSION -> openFloating(newSession = true, command = null)
            TianquanActions.RUN_COMMAND -> openFloating(
                newSession = false,
                command = intent?.getStringExtra(TianquanActions.EXTRA_COMMAND),
            )
            // FLOATING_WINDOW 及其它：默认打开浮动窗（复用会话）
            else -> openFloating(newSession = false, command = null)
        }
        finish()
    }

    private fun openFloating(newSession: Boolean, command: String?) {
        if (command != null && command.isBlank()) return
        if (!TianquanFloatingWindow.granted(this)) {
            Toast.makeText(this, "需要悬浮窗权限（显示在其他应用上层）", Toast.LENGTH_SHORT).show()
            TianquanFloatingWindow.requestPermission(this)
            return
        }
        runCatching {
            TianquanFloatingWindow.start(this, newSession = newSession, command = command)
        }.onFailure {
            Toast.makeText(this, "启动浮动终端失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }
}
