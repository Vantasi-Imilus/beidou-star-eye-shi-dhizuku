package org.beidou.stareye

import android.content.Context

/**
 * 应用身份 —— 大标题即模式开关。
 *
 * 首页大标题可被用户随手编辑：恰为「逆北斗·群星之眼」时进入**反向星界模式**
 * （全局调色板反色 + 边框锐利化 + 首页切换为反向星界面板）；
 * 改成其他任意文本则回归正常界面。模式随标题持久化。
 */
object BeidouIdentity {

    const val DEFAULT_TITLE = "北斗·群星之眼"
    const val REVERSED_TITLE = "逆北斗·群星之眼"

    private const val PREFS = "beidou_identity"
    private const val KEY_TITLE = "title"

    fun title(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TITLE, DEFAULT_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TITLE

    /** 写入标题（空白回落为默认标题） */
    fun setTitle(context: Context, title: String) {
        val normalized = title.trim().ifBlank { DEFAULT_TITLE }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, normalized)
            .apply()
    }

    /** 反向星界模式：标题恰为 [REVERSED_TITLE] */
    fun isReversed(context: Context): Boolean = title(context) == REVERSED_TITLE
}
