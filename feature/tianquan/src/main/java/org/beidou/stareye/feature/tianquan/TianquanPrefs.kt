package org.beidou.stareye.feature.tianquan

import android.content.Context
import android.content.SharedPreferences

/** 光标样式（对应 DECSCUSR） */
enum class CursorStyle(val label: String) {
    BLOCK("方块"),
    UNDERLINE("下划线"),
    BAR("竖条"),
}

/**
 * 天权中枢配置持久化。
 */
object TianquanPrefs {

    private const val PREFS = "tianquan_prefs"
    const val DEFAULT_SHELL = "/system/bin/sh"

    /** 可变字体列表路径：用户把 .ttf 放到此处即可选用（与 Termux-X 的字体目录机制思路一致） */
    const val FONT_DIR_HINT = "fonts"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 字号 ──

    fun fontSize(context: Context): Int = prefs(context).getInt("font_size", 13)

    fun setFontSize(context: Context, size: Int) {
        prefs(context).edit().putInt("font_size", size.coerceIn(6, 32)).apply()
    }

    // ── 配色 ──

    fun theme(context: Context): TerminalTheme =
        TerminalThemes.byId(prefs(context).getString("theme", "starry"))

    fun setTheme(context: Context, id: String) {
        prefs(context).edit().putString("theme", id).apply()
    }

    // ── 特权通道 ──

    fun channel(context: Context): TerminalChannel =
        TerminalChannel.entries.firstOrNull { it.name == prefs(context).getString("channel", null) }
            ?: TerminalChannel.AUTO

    fun setChannel(context: Context, channel: TerminalChannel) {
        prefs(context).edit().putString("channel", channel.name).apply()
    }

    // ── Shell ──

    fun shellPath(context: Context): String =
        prefs(context).getString("shell", DEFAULT_SHELL) ?: DEFAULT_SHELL

    fun setShellPath(context: Context, path: String) {
        prefs(context).edit().putString("shell", path.trim().ifBlank { DEFAULT_SHELL }).apply()
    }

    // ── 光标 ──

    fun cursorStyle(context: Context): CursorStyle =
        CursorStyle.entries.firstOrNull { it.name == prefs(context).getString("cursor_style", null) }
            ?: CursorStyle.BLOCK

    fun setCursorStyle(context: Context, style: CursorStyle) {
        prefs(context).edit().putString("cursor_style", style.name).apply()
    }

    /** 光标闪烁周期（毫秒）；0 表示不闪 */
    fun cursorBlinkMs(context: Context): Int = prefs(context).getInt("cursor_blink", 600)

    fun setCursorBlinkMs(context: Context, ms: Int) {
        prefs(context).edit().putInt("cursor_blink", ms.coerceIn(0, 2000)).apply()
    }

    // ── 回滚历史 ──

    fun scrollbackRows(context: Context): Int = prefs(context).getInt("scrollback", 2000)

    fun setScrollbackRows(context: Context, rows: Int) {
        prefs(context).edit().putInt("scrollback", rows.coerceIn(100, 20000)).apply()
    }

    // ── 行为开关 ──

    /** 返回键行为：true=发送 ESC（vim 等友好），false=交还系统 */
    fun backKeyAsEscape(context: Context): Boolean =
        prefs(context).getBoolean("back_key_escape", true)

    fun setBackKeyAsEscape(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean("back_key_escape", v).apply()
    }

    /** 铃声行为：震动 / 蜂鸣 / 忽略 */
    fun bellVibrate(context: Context): Boolean = prefs(context).getBoolean("bell_vibrate", true)

    fun setBellVibrate(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean("bell_vibrate", v).apply()
    }

    /** 终端内点击 URL 时是否用浏览器打开 */
    fun openUrlOnClick(context: Context): Boolean =
        prefs(context).getBoolean("open_url_on_click", true)

    fun setOpenUrlOnClick(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean("open_url_on_click", v).apply()
    }

    /** 真彩直通：关闭后把 24 位色近似到 256 色 */
    fun trueColor(context: Context): Boolean = prefs(context).getBoolean("true_color", true)

    fun setTrueColor(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean("true_color", v).apply()
    }

    /** 鼠标追踪：允许程序接收触摸事件（需程序主动开启时才生效） */
    fun mouseTrackingEnabled(context: Context): Boolean =
        prefs(context).getBoolean("mouse_tracking", true)

    fun setMouseTrackingEnabled(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean("mouse_tracking", v).apply()
    }

    // ── 附加键布局（JSON，与 Termux 的 extra-keys 同思路：每行一个字符串数组） ──

    fun extraKeys(context: Context): String =
        prefs(context).getString("extra_keys", "") ?: ""

    fun setExtraKeys(context: Context, json: String) {
        prefs(context).edit().putString("extra_keys", json).apply()
    }

    /**
     * 自定义配色：用户覆盖 16 色 ANSI 调色板的某一项（id → 0xRRGGBB），
     * 与 Termux 的 `~/.termux/colors.properties` 机制对应。
     */
    fun colorOverride(context: Context, key: String): Int? {
        val v = prefs(context).getInt("color_$key", -1)
        return if (v == -1) null else v
    }

    fun setColorOverride(context: Context, key: String, rgb: Int?) {
        prefs(context).edit().apply {
            if (rgb == null) remove("color_$key") else putInt("color_$key", rgb)
        }.apply()
    }
}
