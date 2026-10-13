package org.beidou.stareye.feature.yaoguang

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 快捷方式（固定 + 动态）。
 *
 * 动作取值与雹（Hail）一致，便于用户沿用既有习惯；
 * 快捷方式与磁贴统一经透明入口 [YaoguangApiActivity] 下发执行。
 */
object YaoguangShortcuts {

    const val ACTION_NONE = "none"
    const val ACTION_FREEZE_ALL = "freeze_all"
    const val ACTION_UNFREEZE_ALL = "unfreeze_all"
    const val ACTION_FREEZE_NON_WHITELISTED = "freeze_non_whitelisted"
    const val ACTION_LOCK = "lock"
    const val ACTION_LOCK_FREEZE = "lock_freeze"
    const val ACTION_AUTO_FREEZE = "auto_freeze"

    /** 可固定的动作型快捷方式 */
    val PIN_ACTIONS = listOf(
        ACTION_FREEZE_ALL,
        ACTION_UNFREEZE_ALL,
        ACTION_FREEZE_NON_WHITELISTED,
        ACTION_LOCK,
        ACTION_LOCK_FREEZE,
    )

    /** 动态快捷方式可选动作（含「无」） */
    val DYNAMIC_ACTIONS = listOf(ACTION_NONE) + PIN_ACTIONS

    /** 磁贴可选动作（含「自动冻结」开关） */
    val TILE_ACTIONS = listOf(ACTION_AUTO_FREEZE) + PIN_ACTIONS

    fun label(action: String): String = when (action) {
        ACTION_NONE -> "无"
        ACTION_FREEZE_ALL -> "冻结全部"
        ACTION_UNFREEZE_ALL -> "解冻全部"
        ACTION_FREEZE_NON_WHITELISTED -> "冻结非白名单"
        ACTION_LOCK -> "锁屏"
        ACTION_LOCK_FREEZE -> "锁屏并冻结"
        ACTION_AUTO_FREEZE -> "自动冻结"
        else -> action
    }

    private fun suffixOf(action: String): String = when (action) {
        ACTION_FREEZE_ALL -> YaoguangApi.FREEZE_ALL
        ACTION_UNFREEZE_ALL -> YaoguangApi.UNFREEZE_ALL
        ACTION_FREEZE_NON_WHITELISTED -> YaoguangApi.FREEZE_NON_WHITELISTED
        ACTION_LOCK -> YaoguangApi.LOCK
        ACTION_LOCK_FREEZE -> YaoguangApi.LOCK_FREEZE
        else -> YaoguangApi.FREEZE_AUTO
    }

    private fun shortcutIcon(context: Context): IconCompat =
        IconCompat.createWithResource(context, android.R.drawable.ic_lock_lock)

    /** 快捷方式 / 磁贴统一下发的 Intent */
    fun intentFor(context: Context, action: String, packageName: String? = null, tagId: Int? = null): Intent =
        Intent(context, YaoguangApiActivity::class.java).apply {
            this.action = YaoguangApi.actionOf(context, suffixOf(action))
            packageName?.let { putExtra(YaoguangApi.EXTRA_PACKAGE, it) }
            tagId?.let { putExtra(YaoguangApi.EXTRA_TAG, it) }
        }

    private fun tagIntentFor(context: Context, tagId: Int, frozen: Boolean): Intent =
        Intent(context, YaoguangApiActivity::class.java).apply {
            action = YaoguangApi.actionOf(
                context, if (frozen) YaoguangApi.FREEZE_TAG else YaoguangApi.UNFREEZE_TAG,
            )
            putExtra(YaoguangApi.EXTRA_TAG, tagId)
        }

    /** 请求固定一个动作型快捷方式（由桌面弹确认框） */
    fun requestPin(context: Context, action: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return
        val shortcut = ShortcutInfoCompat.Builder(context, "pin_${action}_${System.currentTimeMillis()}")
            .setShortLabel(label(action))
            .setIcon(shortcutIcon(context))
            .setIntent(intentFor(context, action))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /** 请求固定「某标签 · 冻结 / 解冻」快捷方式 */
    fun requestPinTag(context: Context, tag: YaoguangTag, frozen: Boolean) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return
        val shortcut = ShortcutInfoCompat
            .Builder(context, "pin_tag_${tag.id}_$frozen")
            .setShortLabel("${tag.name}·${if (frozen) "冻结" else "解冻"}")
            .setIcon(shortcutIcon(context))
            .setIntent(tagIntentFor(context, tag.id, frozen))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /** 按设置刷新动态快捷方式 */
    fun refreshDynamic(context: Context) {
        val action = YaoguangStore.dynamicShortcutAction(context)
        if (action == ACTION_NONE) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            return
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "dynamic_action")
            .setShortLabel(label(action))
            .setIcon(shortcutIcon(context))
            .setIntent(intentFor(context, action))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
    }
}
