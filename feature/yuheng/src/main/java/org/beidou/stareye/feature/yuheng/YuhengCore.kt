package org.beidou.stareye.feature.yuheng

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import org.beidou.stareye.feature.kaiyang.KaiyangState

/**
 * 玉衡中枢 —— 核心基座（自研实现，零拷贝）。
 *
 * 与「开阳中枢」共用同一个 Device Owner admin 组件（同一应用，互不冲突）：
 * 开阳负责 Dhizuku 授权生态，玉衡负责 OwnDroid 式的设备深度管控。
 */
object YuhengCore {

    /** 本应用的设备管理组件（由开阳中枢提供，已作为 Device Owner 激活） */
    fun admin(context: Context): ComponentName = KaiyangState.admin(context)

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun um(context: Context): UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    // ── 权限状态 ──

    fun isDeviceOwner(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    fun isProfileOwner(context: Context): Boolean = try {
        dpm(context).isProfileOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    /** 是否具备 Device Owner 级权限（绝大多数功能的硬前提） */
    fun hasOwner(context: Context): Boolean = isDeviceOwner(context) || isProfileOwner(context)

    /** 当前用户是否被本应用管理（Profile Owner 场景） */
    fun isManagedProfile(context: Context): Boolean = try {
        um(context).isManagedProfile
    } catch (_: Throwable) {
        false
    }

    /** 当前进程所属用户 id */
    fun currentUserId(): Int = try {
        android.os.Process.myUid() / 100000
    } catch (_: Throwable) {
        0
    }

    fun currentUser(context: Context): UserHandle =
        android.os.Process.myUserHandle()

    /** 权限状态摘要，供界面显示 */
    fun privilegeSummary(context: Context): String = when {
        isDeviceOwner(context) -> "设备所有者（Device Owner）"
        isProfileOwner(context) -> "资料所有者（Profile Owner）"
        else -> "未激活 · 请先在「开阳中枢」激活设备所有者"
    }

    // ── 安全调用 ──

    /** 操作结果：成功附带可读信息，失败给出原因 */
    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Fail(val message: String) : Result

        val isOk: Boolean get() = this is Ok
        fun text(): String = when (this) {
            is Ok -> message
            is Fail -> "失败：$message"
        }
    }

    private fun ok(msg: String = "已完成"): Result = Result.Ok(msg)
    private fun fail(msg: String): Result = Result.Fail(msg)

    /**
     * 统一异常包装：把 SecurityException / IllegalArgumentException 等
     * 转成可读的失败信息，避免崩溃。
     */
    fun call(description: String, block: () -> Unit): Result = try {
        block()
        ok(if (description.isBlank()) "已完成" else "$description 已完成")
    } catch (e: SecurityException) {
        fail("$description 被拒绝：${e.message ?: "权限不足"}")
    } catch (e: IllegalArgumentException) {
        fail("$description 参数不被接受：${e.message ?: ""}")
    } catch (e: UnsupportedOperationException) {
        fail("$description 当前系统不支持：${e.message ?: ""}")
    } catch (e: Throwable) {
        fail("$description 异常：${e.message ?: e::class.java.simpleName}")
    }

    /** 有返回值的统一异常包装 */
    fun <T> callFor(default: T, block: () -> T): T = try {
        block()
    } catch (_: Throwable) {
        default
    }

    fun requirementNote(minApi: Int, ownerOnly: Boolean = true): String? = when {
        Build.VERSION.SDK_INT < minApi -> "需要 Android ${apiToVersion(minApi)} 及以上"
        ownerOnly -> null
        else -> null
    }

    fun apiToVersion(api: Int): String = when {
        api >= 36 -> "16"
        api >= 35 -> "15"
        api >= 34 -> "14"
        api >= 33 -> "13"
        api >= 31 -> "12"
        api >= 30 -> "11"
        api >= 29 -> "10"
        api >= 28 -> "9"
        api >= 26 -> "8"
        api >= 24 -> "7"
        else -> "API $api"
    }

    // ── 组织信息（设置一次即可在系统 UI 显示） ──

    fun organizationName(context: Context): String = callFor("") {
        dpm(context).getOrganizationName(admin(context))?.toString() ?: ""
    }

    fun setOrganizationName(context: Context, name: String): Result =
        call("设置组织名称") { dpm(context).setOrganizationName(admin(context), name) }

    fun deviceOwnerLockScreenInfo(context: Context): String = callFor("") {
        dpm(context).deviceOwnerLockScreenInfo?.toString() ?: ""
    }

    fun setDeviceOwnerLockScreenInfo(context: Context, text: String): Result =
        call("设置锁屏信息") { dpm(context).setDeviceOwnerLockScreenInfo(admin(context), text) }
}
