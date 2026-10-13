package org.beidou.stareye.feature.tianxuan

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import org.beidou.stareye.core.freeze.FreezeEvents
import org.beidou.stareye.core.freeze.FreezeState
import org.beidou.stareye.core.shell.ShizukuShell
import java.io.File

/** 应用过滤维度 */
enum class AppFilter { ALL, USER, SYSTEM }

/**
 * 天璇中枢的应用条目（列表 + 详情共用）。
 */
data class TianxuanApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val sourceDir: String?,
    val dataDir: String?,
    val targetSdk: Int,
    val enabled: Boolean,
    val icon: Bitmap?,
)

/**
 * 天璇中枢 —— App Manager 核心逻辑（方案A：核心能力子集，自研实现）。
 *
 * 能力：应用列表/详情、冻结解冻、强停、清数据、卸载、导出 APK、操作记录。
 * 权限通道：
 *  · 天枢（Shizuku）→ shell / root 执行 am / pm 命令（强停、清数据、卸载、导出 APK）
 *  · 开阳（Device Owner）→ DevicePolicyManager 挂起/恢复应用（冻结解冻）
 */
object TianxuanManager {

    // ── 权限通道状态 ──

    fun shizukuReady(): Boolean = ShizukuShell.ready()

    fun isDeviceOwner(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    /** 本应用（开阳中枢）作为 Device Owner 时的活跃 admin 组件 */
    private fun ownerAdmin(context: Context): ComponentName? = try {
        dpm(context).activeAdmins?.firstOrNull { it.packageName == context.packageName }
    } catch (_: Throwable) {
        null
    }

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    // ── 应用列表 ──

    fun listApps(context: Context, filter: AppFilter): List<TianxuanApp> {
        val pm = context.packageManager
        val infos = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            return emptyList()
        }
        return infos.mapNotNull { ai ->
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (filter == AppFilter.USER && isSystem) return@mapNotNull null
            if (filter == AppFilter.SYSTEM && !isSystem) return@mapNotNull null

            val pkg = ai.packageName
            val pi = try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: Throwable) {
                null
            }
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (_: Throwable) {
                pkg
            }
            val icon = try {
                ai.loadIcon(pm).toBitmap(96, 96)
            } catch (_: Throwable) {
                null
            }
            TianxuanApp(
                packageName = pkg,
                label = label,
                uid = ai.uid,
                versionName = pi?.versionName,
                versionCode = packageVersionCode(pi),
                isSystem = isSystem,
                installTime = pi?.firstInstallTime ?: 0L,
                updateTime = pi?.lastUpdateTime ?: 0L,
                sourceDir = ai.sourceDir,
                dataDir = ai.dataDir,
                targetSdk = ai.targetSdkVersion,
                enabled = ai.enabled,
                icon = icon,
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun packageVersionCode(pi: android.content.pm.PackageInfo?): Long {
        if (pi == null) return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    }

    /** APK 大小（best-effort：非本应用目录可能无读取权限时返回 null） */
    fun apkSizeBytes(app: TianxuanApp): Long? {
        val dir = app.sourceDir ?: return null
        return File(dir).length().takeIf { it > 0 }
    }

    // ── 冻结 / 解冻 ──

    /**
     * 冻结状态读自 core:freeze 统一判定源（挂起 / 隐藏 / 禁用任一即视为已冻结），
     * 与玉衡 / 瑶光显示完全一致，不再各自实现判定。
     */
    fun isFrozen(context: Context, pkg: String): Boolean = FreezeState.isFrozen(context, pkg)

    fun freeze(context: Context, pkg: String): Result<Unit> {
        val admin = ownerAdmin(context)?.takeIf { isDeviceOwner(context) }
        return if (admin != null) {
            runCatching { dpm(context).setPackagesSuspended(admin, arrayOf(pkg), true) }.map { }
        } else {
            ShizukuShell.run("pm disable-user --user 0 $pkg").map { }
        }.onSuccess { FreezeEvents.notifyChanged(pkg) }
    }

    /**
     * 解冻：按应用当前的实际形态逐一清除（挂起 / 隐藏 / 禁用），
     * 使任意中枢冻结的应用都能在此处真正解开。
     */
    fun unfreeze(context: Context, pkg: String): Result<Unit> = runCatching {
        val flags = FreezeState.flags(context, pkg)
        val admin = ownerAdmin(context)?.takeIf { isDeviceOwner(context) }

        if (flags.suspended) {
            if (admin != null) {
                dpm(context).setPackagesSuspended(admin, arrayOf(pkg), false)
            } else {
                ShizukuShell.run("pm unsuspend --user 0 $pkg").getOrThrow()
            }
        }
        if (flags.hidden) {
            if (admin != null) {
                if (!dpm(context).setApplicationHidden(admin, pkg, false)) error("取消隐藏失败")
            } else {
                ShizukuShell.run("pm unhide --user 0 $pkg").getOrThrow()
            }
        }
        if (flags.disabled) {
            ShizukuShell.run("pm enable --user 0 $pkg").getOrThrow()
        }
        Unit
    }.onSuccess { FreezeEvents.notifyChanged(pkg) }

    // ── 强停 / 清数据 / 卸载 ──

    fun forceStop(pkg: String): Result<Unit> =
        ShizukuShell.run("am force-stop $pkg").map { }

    fun clearData(pkg: String): Result<Unit> =
        ShizukuShell.run("pm clear $pkg").map { }

    fun uninstall(pkg: String): Result<Unit> =
        ShizukuShell.run("pm uninstall --user 0 $pkg").map { }

    // ── 导出 APK ──

    /** 通过 `pm path` 获取主 APK 路径 */
    fun apkPath(pkg: String): String? = try {
        val out = ShizukuShell.run("pm path $pkg").getOrThrow()
        out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
    } catch (_: Throwable) {
        null
    }

    /**
     * 导出应用主 APK 到应用外部文件目录，返回保存路径。
     * 通过 shell 流式读取 APK 字节写入本地，避免直接跨 uid 读写受限。
     */
    fun exportApk(context: Context, app: TianxuanApp): Result<String> = runCatching {
        val path = apkPath(app.packageName) ?: error("无法定位 APK 路径")
        val bytes = ShizukuShell.runBytes("cat ${shellQuote(path)}").getOrThrow()

        val dir = context.getExternalFilesDir("apks") ?: File(context.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        val version = app.versionName?.takeIf { it.isNotBlank() } ?: app.versionCode.toString()
        val file = File(dir, "${app.packageName}-$version.apk")
        file.writeBytes(bytes)
        file.absolutePath
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
