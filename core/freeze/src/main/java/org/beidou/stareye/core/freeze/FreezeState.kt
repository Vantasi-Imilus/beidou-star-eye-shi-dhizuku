package org.beidou.stareye.core.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * 应用冻结状态的统一判定源 —— 天璇 / 玉衡 / 瑶光共用。
 *
 * 判定全部基于 PackageManager 本地查询、不经任何特权通道，故同一应用在任何中枢、
 * 任何时刻读到的状态必然一致；各中枢不得再自行实现一套冻结判定。
 */
object FreezeState {

    /** 冻结的具体形态（强停属瞬态，不计入冻结形态，由 [Flags.stopped] 单独表达） */
    enum class Kind(val label: String) {
        SUSPENDED("已挂起"),
        HIDDEN("已隐藏"),
        DISABLED("已禁用"),
        NONE("正常"),
    }

    /** 一次查询得到的原始状态组合 */
    data class Flags(
        val suspended: Boolean = false,
        val hidden: Boolean = false,
        val disabled: Boolean = false,
        val stopped: Boolean = false,
    ) {
        /**
         * 跨中枢统一的「已冻结」定义：挂起 / 隐藏 / 禁用。
         *
         * 强停不计入：它一旦启动应用即被清除，且安装后从未打开过的应用同样带该标志，
         * 计入会让"刚装的应用"显示为已冻结 —— 单列由各中枢按需使用。
         */
        val frozen: Boolean get() = suspended || hidden || disabled

        /**
         * 具体冻结形态，按 挂起 → 隐藏 → 禁用 取首个命中。
         *
         * 不含强停：安装后从未打开过的应用同样带 stopped 标志，若计入会让
         * 应用列表里大批普通应用被标成"已强停"，故强停只由 [stopped] 表达。
         */
        val kind: Kind
            get() = when {
                suspended -> Kind.SUSPENDED
                hidden -> Kind.HIDDEN
                disabled -> Kind.DISABLED
                else -> Kind.NONE
            }
    }

    /** 查询标志位；应用不存在时全部为 false */
    fun flags(context: Context, packageName: String): Flags {
        val info = applicationInfo(context, packageName) ?: return Flags()
        return flagsOf(info)
    }

    /** 由已取得的 [ApplicationInfo] 直接判定（列表批量场景可免去二次查询） */
    fun flagsOf(info: ApplicationInfo): Flags = Flags(
        suspended = (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
        hidden = isHidden(info),
        disabled = !info.enabled,
        stopped = (info.flags and ApplicationInfo.FLAG_STOPPED) != 0,
    )

    /** 是否处于冻结（各中枢统一口径） */
    fun isFrozen(context: Context, packageName: String): Boolean =
        flags(context, packageName).frozen

    /** 用户可见的形态文案，如「已挂起」 */
    fun describe(context: Context, packageName: String): String =
        flags(context, packageName).kind.label

    /** 需带 MATCH_UNINSTALLED_PACKAGES 才能查到已隐藏 / 已卸载的应用 */
    private fun applicationInfo(context: Context, packageName: String): ApplicationInfo? = try {
        context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
    } catch (_: Throwable) {
        null
    }

    /**
     * 隐藏标志（PRIVATE_FLAG_HIDDEN）无公开 API，与雹（Hail）同法读 privateFlags 低位；
     * 反射不可用时退回公开标志：包可查到但未置 INSTALLED 位即视为已隐藏。
     */
    private fun isHidden(info: ApplicationInfo): Boolean = try {
        (ApplicationInfo::class.java.getField("privateFlags").getInt(info) and 1) != 0
    } catch (_: Throwable) {
        (info.flags and ApplicationInfo.FLAG_INSTALLED) == 0
    }
}
