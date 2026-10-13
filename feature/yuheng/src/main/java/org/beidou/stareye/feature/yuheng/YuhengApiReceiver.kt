package org.beidou.stareye.feature.yuheng

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 玉衡中枢 —— 对外 Intent API（自研实现）。
 *
 * 广播契约（与 OwnDroid 保持一致，便于既有调用方无缝迁入）：
 * ```
 * action  = <本应用包名>.action.<NAME>
 * 组件    = <本应用包名>/.feature.yuheng.YuhengApiReceiver
 * extras  = key:String（必填，密钥）, package:String, permission:String, restriction:String
 * ```
 *
 * 与 OwnDroid 的差异（安全性加强）：密钥校验改为**常量时间比较**，且默认关闭、密钥长度下限 8。
 */
class YuhengApiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val name = action.substringAfterLast('.')

        if (!YuhengPrefs.verifyKey(context, intent.getStringExtra(EXTRA_KEY))) {
            Log.w(TAG, "未授权调用：$name")
            return
        }

        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val permission = intent.getStringExtra(EXTRA_PERMISSION)
        val restriction = intent.getStringExtra(EXTRA_RESTRICTION)

        try {
            when (name) {
                // ── 应用 ──
                "HIDE" -> appOp(context, pkg, "app_hide", true)
                "UNHIDE" -> appOp(context, pkg, "app_hide", false)
                "SUSPEND" -> appOp(context, pkg, "app_suspend", true)
                "UNSUSPEND" -> appOp(context, pkg, "app_suspend", false)
                "BLOCK_UNINSTALL" -> appOp(context, pkg, "app_block_uninstall", true)
                "UNBLOCK_UNINSTALL" -> appOp(context, pkg, "app_block_uninstall", false)
                "DISABLE_METERED_DATA" -> appOp(context, pkg, "app_disable_metered", true)
                "ENABLE_METERED_DATA" -> appOp(context, pkg, "app_disable_metered", false)
                "DISABLE_USER_CONTROL" -> appOp(context, pkg, "app_disable_user_control", true)
                "ENABLE_USER_CONTROL" -> appOp(context, pkg, "app_disable_user_control", false)
                "CLEAR_APP_STORAGE" -> pkg?.let { YuhengOps.clearAppData(context, it) }

                // ── 用户限制 ──
                "ADD_USER_RESTRICTION" -> restriction?.let { YuhengOps.restrictionSet(context, it, true) }
                "CLEAR_USER_RESTRICTION" -> restriction?.let { YuhengOps.restrictionSet(context, it, false) }

                // ── 权限 ──
                "SET_PERMISSION_DEFAULT" -> permissionOp(context, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
                "SET_PERMISSION_GRANTED" -> permissionOp(context, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                "SET_PERMISSION_DENIED" -> permissionOp(context, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED)

                // ── 设备开关 ──
                "SET_CAMERA_DISABLED" -> YuhengOps.systemWrite(context, "camera_disabled", true)
                "SET_CAMERA_ENABLED" -> YuhengOps.systemWrite(context, "camera_disabled", false)
                "SET_SCREEN_CAPTURE_DISABLED" -> YuhengOps.systemWrite(context, "screen_capture_disabled", true)
                "SET_SCREEN_CAPTURE_ENABLED" -> YuhengOps.systemWrite(context, "screen_capture_disabled", false)
                "SET_USB_DISABLED" -> YuhengOps.systemWrite(context, "usb_data_signaling", false)
                "SET_USB_ENABLED" -> YuhengOps.systemWrite(context, "usb_data_signaling", true)

                // ── 设备动作 ──
                "LOCK" -> YuhengOps.systemAction(context, "lock_now")
                "REBOOT" -> YuhengOps.systemAction(context, "reboot")

                else -> Log.w(TAG, "未知 action：$name")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "执行 $name 失败：${e.message}", e)
        }
    }

    private fun appOp(context: Context, pkg: String?, op: String, on: Boolean) {
        if (pkg == null) return
        YuhengOps.appToggle(context, op, pkg, on)
    }

    private fun permissionOp(context: Context, pkg: String?, permission: String?, state: Int) {
        if (pkg == null || permission == null) return
        if (Build.VERSION.SDK_INT < 23) return
        YuhengOps.setPermissionState(context, pkg, permission, state)
    }

    companion object {
        private const val TAG = "YuhengApi"
        const val EXTRA_KEY = "key"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_PERMISSION = "permission"
        const val EXTRA_RESTRICTION = "restriction"

        /** 对外暴露的 action 全清单（供界面展示与文档） */
        val ACTIONS: List<Pair<String, String>> = listOf(
            "HIDE" to "隐藏应用",
            "UNHIDE" to "取消隐藏",
            "SUSPEND" to "挂起应用",
            "UNSUSPEND" to "取消挂起",
            "DISABLE_METERED_DATA" to "禁用计量数据",
            "ENABLE_METERED_DATA" to "启用计量数据",
            "DISABLE_USER_CONTROL" to "禁用用户控制",
            "ENABLE_USER_CONTROL" to "启用用户控制",
            "BLOCK_UNINSTALL" to "阻止卸载",
            "UNBLOCK_UNINSTALL" to "允许卸载",
            "CLEAR_APP_STORAGE" to "清除应用数据",
            "ADD_USER_RESTRICTION" to "添加用户限制",
            "CLEAR_USER_RESTRICTION" to "清除用户限制",
            "SET_PERMISSION_DEFAULT" to "权限设为默认",
            "SET_PERMISSION_GRANTED" to "授予权限",
            "SET_PERMISSION_DENIED" to "拒绝权限",
            "SET_SCREEN_CAPTURE_DISABLED" to "禁止截屏",
            "SET_SCREEN_CAPTURE_ENABLED" to "允许截屏",
            "SET_CAMERA_DISABLED" to "禁用摄像头",
            "SET_CAMERA_ENABLED" to "启用摄像头",
            "SET_USB_DISABLED" to "禁用 USB",
            "SET_USB_ENABLED" to "启用 USB",
            "LOCK" to "立即锁屏",
            "REBOOT" to "重启设备",
        )
    }
}
