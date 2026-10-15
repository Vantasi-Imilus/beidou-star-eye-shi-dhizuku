package org.beidou.stareye.feature.fanxiang

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import org.beidou.stareye.core.shell.ShizukuShell

/**
 * 反向星界 —— 隐藏 API 桥（反射调用，与工程既有的 `Shizuku.newProcess` 同一手法）。
 *
 * 创建与管理工作资料的三个关键 API 均标注 `@SystemApi`（公开 SDK 不可见但运行时存在）：
 *  · [dpmCreateManagedProfile]：设备所有者**静默**开辟托管资料（无需任何用户交互）；
 *  · [contextAsUser]：取目标用户空间的 Context，使其中的 DPM 调用作用于镜像空间；
 *  · [umIsManagedProfile]：判定某用户空间是否为托管资料。
 * 反射失败时各调用点均有可读的降级路径。
 */
internal object RealmHidden {

    /** `DevicePolicyManager.createManagedProfile(ComponentName, String)`（@SystemApi, API 24+） */
    fun dpmCreateManagedProfile(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        name: String,
    ): Result<UserHandle> = runCatching {
        val method = DevicePolicyManager::class.java
            .getMethod("createManagedProfile", ComponentName::class.java, String::class.java)
        val user = method.invoke(dpm, admin, name) as? UserHandle
            ?: error("createManagedProfile 返回 null")
        user
    }.onFailure {
        android.util.Log.w("Realm", "createManagedProfile 调用失败", it)
    }

    /** `Context.createContextAsUser(UserHandle, int)`（@SystemApi, API 24+） */
    fun contextAsUser(context: Context, user: UserHandle): Context? = runCatching {
        Context::class.java
            .getMethod("createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType)
            .invoke(context, user, 0) as? Context
    }.getOrNull()

    /** `UserManager.isManagedProfile(UserHandle)`（@SystemApi） */
    fun umIsManagedProfile(um: UserManager, user: UserHandle): Boolean = runCatching {
        UserManager::class.java
            .getMethod("isManagedProfile", UserHandle::class.java)
            .invoke(um, user) as? Boolean ?: false
    }.getOrDefault(false)

    /** `LauncherApps.startMainActivity` 在多用户下可能需要系统身份，失败即回退 shell */
    fun isSecurityFailure(t: Throwable): Boolean =
        t is SecurityException || t.cause is SecurityException

    /** shell 路径：创建托管资料（`Success: created user id N` → id） */
    fun createManagedViaShell(): Int? = runCatching {
        val out = ShizukuShell.run(
            "pm create-user --profileOf 0 --managed '${RealmState.REALM_NAME}'",
        ).getOrThrow()
        parseCreatedId(out)
    }.getOrNull()

    /** 降级路径：创建独立用户空间（部分 ROM 禁止托管资料时使用，隔离效果等价） */
    fun createSecondaryUser(name: String): Int? = runCatching {
        val out = ShizukuShell.run("pm create-user '$name'").getOrThrow()
        parseCreatedId(out)
    }.getOrNull()

    /**
     * 星界路径（华为等 ROM 实测可用）：把本应用设为指定用户的 Profile Owner。
     *
     * 前置：该用户中必须已安装本应用（admin 组件才能被激活），否则报 "Unknown admin"。
     * 成功后本应用在该用户内拥有完整的 DevicePolicyManager 管理能力
     * （`installExistingPackage` / `setApplicationHidden` / `setPackagesSuspended` / `removeUser` …），
     * 效果与标准托管资料等价，且**不需要设备所有者**。
     */
    fun setProfileOwner(userId: Int, admin: ComponentName): Boolean = runCatching {
        val out = ShizukuShell.run(
            "dpm set-profile-owner --user $userId '${admin.flattenToString()}'",
        ).getOrThrow()
        out.contains("Success")
    }.getOrDefault(false)

    /** 把本应用安装进指定用户空间（星界路径的前置步骤） */
    fun installSelfToUser(packageName: String, userId: Int): Boolean = runCatching {
        val out = ShizukuShell.run(
            "cmd package install-existing --user $userId $packageName",
        ).getOrThrow()
        out.contains("installed") || out.contains("Success")
    }.getOrDefault(false)

    private fun parseCreatedId(out: String): Int? =
        Regex("id\\s+(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * 判定用户空间是否仍存在（经天枢 shell `pm list users`）。
     *
     * 返回 null 表示**无法判定**（shell 未就绪）——调用方应保守保留记录，避免误删。
     */
    fun userExistsOrNull(id: Int): Boolean? =
        ShizukuShell.run("pm list users").getOrNull()?.let { it.contains("UserInfo{$id:") }

    // ── 星界空间限制（玉衡映射） ──

    /** 查询星界空间的限制状态（在该用户上下文中读取） */
    fun restrictionStates(
        context: Context,
        user: UserHandle,
        keys: List<String>,
    ): Map<String, Boolean> = runCatching {
        val userContext = contextAsUser(context, user) ?: return emptyMap()
        val um = userContext.getSystemService(Context.USER_SERVICE) as UserManager
        keys.associateWith { um.hasUserRestriction(it) }
    }.getOrDefault(emptyMap())

    /** 设置 / 清除星界空间的限制（经该用户的 DPM，需本应用持有其管理权） */
    fun setRestriction(context: Context, key: String, enabled: Boolean): Result<Unit> = runCatching {
        val user = RealmState.realmUser(context) ?: error("星界尚未开辟")
        val userContext = contextAsUser(context, user) ?: error("无法进入星界上下文")
        val dpm = userContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        check(dpm.isProfileOwnerApp(context.packageName)) { "本应用未持有该空间管理权" }
        val admin = org.beidou.stareye.feature.kaiyang.KaiyangState.admin(context)
        if (enabled) dpm.addUserRestriction(admin, key) else dpm.clearUserRestriction(admin, key)
    }

    /** 诊断：枚举本 ROM 的 DPM 中与「创建用户 / 资料」相关的方法（能力裁剪排查用） */
    fun dumpCreateMethods(dpm: DevicePolicyManager): List<String> = runCatching {
        DevicePolicyManager::class.java.methods
            .filter {
                val n = it.name.lowercase()
                (n.contains("create") || n.contains("provision")) &&
                    (n.contains("user") || n.contains("profile"))
            }
            .map { m -> "${m.name}(${m.parameterTypes.joinToString { it.simpleName }})" }
            .sorted()
    }.getOrDefault(emptyList())
}
