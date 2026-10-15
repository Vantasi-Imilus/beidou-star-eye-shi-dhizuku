package org.beidou.stareye.feature.fanxiang

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.UserHandle
import androidx.core.graphics.drawable.toBitmap
import org.beidou.stareye.core.shell.ShizukuShell
import org.beidou.stareye.feature.kaiyang.KaiyangState

/** 反向星界中的应用条目 */
data class RealmApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    /** 当前是否处于冻结（挂起 ∨ 隐藏 ∨ 禁用） */
    val frozen: Boolean,
    /** 是否驻留于星界（false = 主界应用，可映照） */
    val inRealm: Boolean,
)

/**
 * 反向星界 —— 星界应用管理。
 *
 * 星界应用运行在独立用户空间，全部经「跨用户」通道访问：
 *  · 列表：`LauncherApps.getActivityList(null, user)`（公开 API，无需额外权限）；
 *  · 映照（把主界应用复制进星界）：星界上下文 DPM `installExistingPackage`（设备所有者静默），
 *    回退天枢 shell `cmd package install-existing --user <id>`；
 *  · 冻结：星界上下文 DPM `setApplicationHidden`（与炼妖壶同法），回退 shell；
 *  · 启动 / 卸载 / 强停：LauncherApps 与天枢 shell。
 */
object RealmApps {

    private fun launcher(context: Context): LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private fun admin(context: Context) = KaiyangState.admin(context)

    /** 星界用户空间内的 DPM 实例（跨用户上下文，隐藏 API 反射） */
    private fun profileDpm(context: Context, user: UserHandle): DevicePolicyManager {
        val profileContext = RealmHidden.contextAsUser(context, user)
            ?: error("无法进入星界上下文（createContextAsUser 不可用）")
        return profileContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    // ── 列表 ──

    /**
     * 星界内的用户应用。
     *
     * 包名经天枢 shell `pm list packages --user <id>` 查询
     * （跨用户的 LauncherApps 对普通用户空间不可用，实测受限）；
     * 名称与图标取自**主界同名应用**（同一 APK，元数据一致）。
     * 系统预装应用不计入（数量庞大且非映照目标）。
     */
    fun list(context: Context): List<RealmApp> {
        val user = RealmState.realmUser(context) ?: return emptyList()
        val id = RealmState.userIdOf(user)
        val all = ShizukuShell.run("pm list packages --user $id").getOrNull() ?: return emptyList()
        // 已禁用的应用（冻结形态之一）
        val disabled = ShizukuShell.run("pm list packages -d --user $id").getOrNull()
            ?.let { parsePackages(it) }
            ?: emptySet()
        val realmPackages = parsePackages(all)

        val pm = context.packageManager
        return realmPackages
            .asSequence()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    ?: return@mapNotNull null
                if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                RealmApp(
                    packageName = pkg,
                    label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg),
                    icon = runCatching { info.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                    isSystem = false,
                    frozen = pkg in disabled,
                    inRealm = true,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** 解析 `pm list packages` 输出为包名集合 */
    private fun parsePackages(out: String): Set<String> = out.lineSequence()
        .map { it.removePrefix("package:").trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    /** 主界已安装、尚未映照进星界的用户应用 */
    fun mirrorable(context: Context): List<RealmApp> {
        val pm = context.packageManager
        val existing = list(context).map { it.packageName }.toSet()
        return try {
            pm.getInstalledApplications(0)
                .filter {
                    it.packageName != context.packageName &&
                        it.packageName !in existing &&
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .map { ai ->
                    RealmApp(
                        packageName = ai.packageName,
                        label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
                        icon = runCatching { ai.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                        isSystem = false,
                        frozen = false,
                        inRealm = false,
                    )
                }
                .sortedBy { it.label.lowercase() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // ── 映照（克隆进星界） ──

    /** 映照：把主界应用复制进星界。设备所有者静默优先，天枢 shell 兜底。 */
    fun mirror(context: Context, packageName: String): Result<Unit> {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) {
            return Result.failure(IllegalArgumentException("非法包名：$packageName"))
        }
        val user = RealmState.realmUser(context)
            ?: return Result.failure(IllegalStateException("请先开辟反向星界"))
        val dpmResult = runCatching {
            val manager = profileDpm(context, user)
            check(manager.installExistingPackage(admin(context), packageName)) { "系统拒绝了映照请求" }
        }
        if (dpmResult.isSuccess) return Result.success(Unit)
        return ShizukuShell.run(
            "cmd package install-existing --user ${RealmState.userIdOf(user)} $packageName",
        ).map { }
    }

    // ── 单应用操作 ──

    /**
     * 冻结 / 解冻（双模式）：
     *  · 托管资料：星界上下文 DPM `setApplicationHidden`（与炼妖壶同法）；
     *  · 降级独立空间：经天枢 shell —— 先 `hide/unhide`，无权限（部分 ROM）时退
     *    `disable-user/enable`，两者达成的隔离效果一致。
     */
    fun setFrozen(context: Context, packageName: String, frozen: Boolean): Result<Unit> {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) {
            return Result.failure(IllegalArgumentException("非法包名：$packageName"))
        }
        val user = RealmState.realmUser(context)
            ?: return Result.failure(IllegalStateException("星界尚未开辟"))
        val id = RealmState.userIdOf(user)

        // 先试 DPM（本应用持有该空间管理权时可用，不依赖天枢）
        runCatching {
            val manager = profileDpm(context, user)
            check(manager.isProfileOwnerApp(context.packageName)) { "非该空间的管理者" }
            if (!manager.setApplicationHidden(admin(context), packageName, frozen)) {
                // setApplicationHidden 对"已是该状态"返回 false —— 校验实际状态
                check(manager.isApplicationHidden(admin(context), packageName) == frozen) {
                    "系统拒绝了状态变更"
                }
            }
        }.onSuccess { return Result.success(Unit) }

        val commands = buildList {
            if (frozen) {
                add("pm hide --user $id $packageName")
                add("pm disable-user --user $id $packageName")
            } else {
                add("pm unhide --user $id $packageName")
                add("pm enable --user $id $packageName")
            }
        }
        var last: Throwable? = null
        for (command in commands) {
            val result = ShizukuShell.run(command)
            if (result.isSuccess) return Result.success(Unit)
            last = result.exceptionOrNull()
        }
        return Result.failure(last ?: IllegalStateException("状态变更失败"))
    }

    /** 启动星界应用 */
    fun launch(context: Context, app: RealmApp): Result<Unit> = runCatching {
        val user = RealmState.realmUser(context) ?: error("星界尚未开辟")
        if (app.frozen) {
            setFrozen(context, app.packageName, false).getOrThrow()
        }
        val activity = launcher(context).getActivityList(app.packageName, user).firstOrNull()
            ?: error("星界中找不到可启动入口")
        runCatching {
            launcher(context).startMainActivity(activity.componentName, user, null, null)
        }.onFailure {
            // 回退：天枢 shell 跨用户启动
            ShizukuShell.run(
                "am start --user ${RealmState.userIdOf(user)} -n ${activity.componentName.flattenToShortString()}",
            ).getOrThrow()
        }
        Unit
    }

    /** 从星界移除（仅卸载该用户空间中的副本，不影响主界本体） */
    fun remove(context: Context, packageName: String): Result<Unit> {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) {
            return Result.failure(IllegalArgumentException("非法包名：$packageName"))
        }
        val user = RealmState.realmUser(context)
            ?: return Result.failure(IllegalStateException("星界尚未开辟"))
        return ShizukuShell.run("pm uninstall --user ${RealmState.userIdOf(user)} $packageName").map { }
    }

    /** 强停（天枢 shell，跨用户） */
    fun forceStop(context: Context, packageName: String): Result<Unit> {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) {
            return Result.failure(IllegalArgumentException("非法包名：$packageName"))
        }
        val user = RealmState.realmUser(context)
            ?: return Result.failure(IllegalStateException("星界尚未开辟"))
        return ShizukuShell.run(
            "am force-stop --user ${RealmState.userIdOf(user)} $packageName",
        ).map { }
    }
}
