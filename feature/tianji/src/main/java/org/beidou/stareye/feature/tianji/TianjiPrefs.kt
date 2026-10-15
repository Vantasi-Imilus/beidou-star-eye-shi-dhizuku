package org.beidou.stareye.feature.tianji

import android.content.Context
import android.content.SharedPreferences

/** 安装原因（对应 `pm install --install-reason` 的 0..4） */
enum class InstallReason(val code: Int, val label: String) {
    UNKNOWN(0, "未知"),
    POLICY(1, "设备政策"),
    DEVICE_RESTORE(2, "设备还原"),
    DEVICE_SETUP(3, "设备初始化"),
    USER(4, "用户"),
}

/**
 * 天玑中枢安装配置。
 */
data class TianjiOptions(
    val authorizer: Authorizer = Authorizer.AUTO,
    val installReason: InstallReason = InstallReason.USER,
    /** 安装请求方（installer package name），空表示不指定 */
    val installerPackage: String = "",
    val forAllUsers: Boolean = false,
    val allowTestPackages: Boolean = true,
    val allowDowngrade: Boolean = false,
    /** 归档安装时默认勾选全部分包 */
    val selectAllSplits: Boolean = true,
    val requireBiometrics: Boolean = false,
    /** 安装完成后，若目标应用处于冻结（挂起/隐藏/禁用）则自动解冻 */
    val unfreezeAfterInstall: Boolean = true,
)

/**
 * 天玑中枢配置与黑名单持久化（SharedPreferences，自研实现）。
 */
object TianjiPrefs {

    private const val PREFS = "tianji_prefs"
    private const val KEY_BLACKLIST = "blacklist"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 安装配置 ──

    @Synchronized
    fun options(context: Context): TianjiOptions {
        val p = prefs(context)
        return TianjiOptions(
            authorizer = enumOr(p.getString("authorizer", null), Authorizer.AUTO),
            installReason = enumOr(p.getString("install_reason", null), InstallReason.USER),
            installerPackage = p.getString("installer_package", "") ?: "",
            forAllUsers = p.getBoolean("for_all_users", false),
            allowTestPackages = p.getBoolean("allow_test", true),
            allowDowngrade = p.getBoolean("allow_downgrade", false),
            selectAllSplits = p.getBoolean("select_all_splits", true),
            requireBiometrics = p.getBoolean("require_biometrics", false),
            unfreezeAfterInstall = p.getBoolean("unfreeze_after_install", true),
        )
    }

    @Synchronized
    fun saveOptions(context: Context, o: TianjiOptions) {
        prefs(context).edit()
            .putString("authorizer", o.authorizer.name)
            .putString("install_reason", o.installReason.name)
            .putString("installer_package", o.installerPackage.trim())
            .putBoolean("for_all_users", o.forAllUsers)
            .putBoolean("allow_test", o.allowTestPackages)
            .putBoolean("allow_downgrade", o.allowDowngrade)
            .putBoolean("select_all_splits", o.selectAllSplits)
            .putBoolean("require_biometrics", o.requireBiometrics)
            .putBoolean("unfreeze_after_install", o.unfreezeAfterInstall)
            .apply()
    }

    // ── 禁止安装的应用（黑名单）──

    @Synchronized
    fun blacklist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLACKLIST, emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun addBlacklist(context: Context, pkg: String) {
        val clean = pkg.trim()
        if (clean.isEmpty()) return
        prefs(context).edit().putStringSet(KEY_BLACKLIST, blacklist(context) + clean).apply()
    }

    @Synchronized
    fun removeBlacklist(context: Context, pkg: String) {
        prefs(context).edit().putStringSet(KEY_BLACKLIST, blacklist(context) - pkg).apply()
    }

    @Synchronized
    fun clearBlacklist(context: Context) {
        prefs(context).edit().remove(KEY_BLACKLIST).apply()
    }

    /** 该包名是否被禁止安装 */
    fun isBlocked(context: Context, pkg: String): Boolean = pkg in blacklist(context)

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback
}
