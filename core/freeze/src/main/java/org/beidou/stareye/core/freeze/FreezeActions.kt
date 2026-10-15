package org.beidou.stareye.core.freeze

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import org.beidou.stareye.core.shell.ShizukuShell

/**
 * 冻结的执行侧 —— 按应用当前实际形态逐一清除（挂起 / 隐藏 / 禁用）。
 *
 * 判定仍由 [FreezeState] 统一提供；执行优先走设备所有者（DPM 公开 API，无需额外进程），
 * 无 Device Owner 时回退天枢 shell。供各中枢复用（如天玑安装完成后自动解冻被冻结的应用）。
 */
object FreezeActions {

    /** 解开应用的全部冻结形态；已是正常状态时直接成功 */
    fun unfreeze(context: Context, packageName: String): Result<Unit> {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) {
            return Result.failure(IllegalArgumentException("非法包名：$packageName"))
        }
        val flags = FreezeState.flags(context, packageName)
        if (!flags.frozen) return Result.success(Unit)
        return runCatching {
            if (flags.suspended) clearSuspended(context, packageName)
            if (flags.hidden) clearHidden(context, packageName)
            if (flags.disabled) clearDisabled(packageName)
        }.onSuccess { FreezeEvents.notifyChanged(packageName) }
    }

    /** 本应用作为设备所有者时的活跃 admin 组件（不依赖开阳中枢的类，避免模块环依赖） */
    private fun ownerAdmin(context: Context): ComponentName? = try {
        val manager = dpm(context)
        if (!manager.isDeviceOwnerApp(context.packageName)) {
            null
        } else {
            manager.activeAdmins?.firstOrNull { it.packageName == context.packageName }
        }
    } catch (_: Throwable) {
        null
    }

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun clearSuspended(context: Context, packageName: String) {
        val admin = ownerAdmin(context)
        if (admin != null) {
            val failed = dpm(context).setPackagesSuspended(admin, arrayOf(packageName), false)
            if (failed.isNotEmpty()) error("系统拒绝了取消挂起请求")
        } else {
            ShizukuShell.run("pm unsuspend --user 0 $packageName").getOrThrow()
        }
    }

    private fun clearHidden(context: Context, packageName: String) {
        val admin = ownerAdmin(context)
        if (admin != null) {
            if (!dpm(context).setApplicationHidden(admin, packageName, false)) error("取消隐藏失败")
        } else {
            ShizukuShell.run("pm unhide --user 0 $packageName").getOrThrow()
        }
    }

    /** 禁用没有对应的 DPM 公开 API，只能经天枢 shell */
    private fun clearDisabled(packageName: String) {
        ShizukuShell.run("pm enable --user 0 $packageName").getOrThrow()
    }
}
