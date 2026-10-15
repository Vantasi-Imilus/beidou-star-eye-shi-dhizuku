package org.beidou.stareye.feature.fanxiang

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import org.beidou.stareye.core.shell.ShizukuShell
import org.beidou.stareye.feature.kaiyang.KaiyangState

/**
 * 反向星界 —— 镜像空间生命周期（双模式自适应）。
 *
 * 星界是系统级的隔离用户空间，内部应用与主界完全独立，仍由七中枢统一管理。
 *
 * **模式自适应**（按 ROM 能力自动选择，界面无需感知）：
 *  · [Mode.MANAGED_PROFILE] 标准托管资料 —— 隐藏 API → 系统配置 flow → shell 三级回退；
 *  · [Mode.SECONDARY_USER] 独立用户空间 —— 部分 ROM（实测华为 EMUI）在系统层面禁止托管资料
 *    （`no_add_managed_profile` 为系统所设且 Android 12 的 DO 无权清除、ManagedProvisioning 被裁剪），
 *    此时降级为经天枢 shell 创建独立用户，隔离效果等价。
 */
object RealmState {

    /** 星界的空间形态 */
    enum class Mode(val label: String) {
        NONE("未开辟"),
        MANAGED_PROFILE("托管资料"),
        SECONDARY_USER("星界空间"),
    }

    /** 星界在系统与本应用中的显示名 */
    const val REALM_NAME = "反向星界"

    private const val PREFS = "fanxiang_realm"
    private const val KEY_FALLBACK_ID = "fallback_user_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun um(context: Context): UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private fun admin(context: Context): ComponentName = KaiyangState.admin(context)

    // ── 状态 ──

    /** 标准托管资料用户（未开辟或降级模式时为 null） */
    private fun managedProfile(context: Context): UserHandle? = try {
        val self = Process.myUserHandle()
        val manager = um(context)
        val others = manager.userProfiles.filter { it != self }
        others.firstOrNull { RealmHidden.umIsManagedProfile(manager, it) }
    } catch (_: Throwable) {
        null
    }

    /** 星界路径：本应用创建的独立用户（记录于 prefs，存在性经 shell 判定） */
    private fun fallbackUser(context: Context): UserHandle? {
        val id = prefs(context).getInt(KEY_FALLBACK_ID, -1)
        if (id <= 0) return null
        val user = UserHandle.getUserHandleForUid(id * 100_000)
        // 注：普通用户不在 UserManager.userProfiles 中（曾因此误删记录）；
        // 改用 shell `pm list users` 判定；无法判定（天枢未就绪）时保守保留记录
        return when (RealmHidden.userExistsOrNull(id)) {
            true -> user
            null -> user // 无法判定 → 保守保留
            false -> {
                prefs(context).edit().remove(KEY_FALLBACK_ID).apply()
                null
            }
        }
    }

    /** 星界用户空间；未开辟时为 null */
    fun realmUser(context: Context): UserHandle? =
        managedProfile(context) ?: fallbackUser(context)

    fun mode(context: Context): Mode = when {
        managedProfile(context) != null -> Mode.MANAGED_PROFILE
        fallbackUser(context) != null -> Mode.SECONDARY_USER
        else -> Mode.NONE
    }

    fun exists(context: Context): Boolean = realmUser(context) != null

    /** 设备所有者已激活（标准模式的硬前提）或天枢就绪（降级模式的前提） */
    fun ownerReady(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    fun shizukuReady(): Boolean = ShizukuShell.ready()

    /** UserHandle 的用户 id（hashCode 即 identifier） */
    fun userIdOf(user: UserHandle): Int = user.hashCode()

    // ── 开辟 ──

    /**
     * 开辟反向星界。按 ROM 能力逐级尝试：
     *  ① 隐藏 API `createManagedProfile`（AOSP 及多数 ROM，静默）；
     *  ② **星界路径**：独立用户 + 安装本应用 + `dpm set-profile-owner`（华为等禁止托管资料的
     *     ROM 实测可用；不需要设备所有者，与 DO 无冲突）；
     *  ③ shell `pm create-user --managed` 兜底。
     *
     * @return 状态描述文本
     */
    fun create(context: Context): Result<String> = runCatching {
        realmUser(context)?.let { return Result.success("星界已存在 · ${mode(context).label}") }

        // ① 隐藏 API（AOSP 静默路径；华为缺此方法会快速失败）
        if (ownerReady(context)) {
            val manager = dpm(context)
            val admin = admin(context)
            runCatching { manager.clearUserRestriction(admin, UserManager.DISALLOW_ADD_MANAGED_PROFILE) }
            RealmHidden.dpmCreateManagedProfile(manager, admin, REALM_NAME).getOrNull()?.let { user ->
                runCatching { manager.setProfileName(admin, REALM_NAME) }
                runCatching { manager.setProfileEnabled(admin) }
                return Result.success("星界已开辟 · 托管资料 user ${userIdOf(user)}")
            }
        }

        // ② 星界路径（主力，实测可用）
        if (ShizukuShell.ready()) {
            RealmHidden.createSecondaryUser(REALM_NAME)?.let { id ->
                val installed = RealmHidden.installSelfToUser(context.packageName, id)
                val adminOk = installed && RealmHidden.setProfileOwner(id, admin(context))
                prefs(context).edit().putInt(KEY_FALLBACK_ID, id).apply()
                return Result.success(
                    if (adminOk) {
                        "星界已开辟 · user $id（本应用已持有管理权）"
                    } else {
                        "星界已开辟 · user $id（管理权设置失败，仅隔离生效）"
                    },
                )
            }
            // ③ shell 创建托管资料兜底（少数 ROM）
            RealmHidden.createManagedViaShell()?.let { id ->
                return Result.success("星界已开辟 · 托管资料 user $id")
            }
        }

        error(
            "星界开辟失败：此 ROM 禁止托管资料，且独立空间创建失败（请确认天枢（Shizuku）已激活）",
        )
    }

    // ── 湮灭 ──

    /** 湮灭反向星界：移除整个镜像空间及其中的全部数据（不可恢复） */
    fun destroy(context: Context): Result<Unit> = runCatching {
        val user = realmUser(context) ?: return Result.success(Unit)
        val id = userIdOf(user)
        if (mode(context) == Mode.MANAGED_PROFILE && ownerReady(context)) {
            dpm(context).removeUser(admin(context), user)
        } else {
            ShizukuShell.run("pm remove-user $id").getOrThrow()
        }
        prefs(context).edit().remove(KEY_FALLBACK_ID).apply()
    }

    /** 星界状态摘要（界面展示用） */
    fun describe(context: Context): String = when (val m = mode(context)) {
        Mode.NONE -> if (ownerReady(context) || shizukuReady()) "未开辟" else "未就绪 · 需开阳（设备所有者）或天枢"
        else -> "已开辟 · ${m.label}（user ${userIdOf(realmUser(context)!!)}）"
    }
}
