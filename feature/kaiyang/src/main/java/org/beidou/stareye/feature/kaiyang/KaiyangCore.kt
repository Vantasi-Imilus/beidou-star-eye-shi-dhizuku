package org.beidou.stareye.feature.kaiyang

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 开阳中枢 Device Owner 状态（对应原版 Dhizuku 的 DhizukuState）。
 */
object KaiyangState {

    fun admin(context: Context): ComponentName =
        ComponentName(context, KaiyangDAReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean = try {
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isDeviceOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    fun isProfileOwner(context: Context): Boolean = try {
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isProfileOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    fun isOwner(context: Context): Boolean = isDeviceOwner(context) || isProfileOwner(context)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /**
     * 可转移的目标：其他包的活跃设备管理员
     * （transferOwnership 要求目标不能与本应用同包，且须为活跃 admin）
     */
    fun transferTargets(context: Context): List<Pair<ComponentName, String>> {
        val manager = dpm(context)
        val pm = context.packageManager
        return (manager.activeAdmins ?: emptyList())
            .filter { it.packageName != context.packageName }
            .map { cn ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(cn.packageName, 0)).toString()
                } catch (_: Throwable) {
                    cn.packageName
                }
                cn to label
            }
    }

    /** 把设备所有者身份转移给 [target]（目标须支持 transfer-ownership） */
    fun transferOwnership(context: Context, target: ComponentName): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            error("转移设备所有者需要 Android 9+")
        }
        dpm(context).transferOwnership(admin(context), target, PersistableBundle())
    }

    /** 主动解除本应用的设备所有者身份 */
    @Suppress("DEPRECATION")
    fun clearOwner(context: Context): Result<Unit> = runCatching {
        dpm(context).clearDeviceOwnerApp(context.packageName)
    }
}

// ── 包名 / 签名工具（移植自原版 Dhizuku） ──

@Suppress("DEPRECATION")
fun PackageManager.getPackageNameForUid(uid: Int): String? = getPackagesForUid(uid)?.first()

@Suppress("DEPRECATION")
fun PackageManager.getPackageInfoForUid(uid: Int): PackageInfo? = runCatching {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    getPackageInfo(getPackageNameForUid(uid) ?: return null, flags)
}.getOrNull()

@Suppress("DEPRECATION")
val PackageInfo.signature: String?
    get() {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        }
        val bytes = signers?.firstOrNull()?.toByteArray() ?: return null
        return MessageDigest.getInstance("sha256")
            .digest(bytes)
            .joinToString("") { "%02X".format(it) }
    }
