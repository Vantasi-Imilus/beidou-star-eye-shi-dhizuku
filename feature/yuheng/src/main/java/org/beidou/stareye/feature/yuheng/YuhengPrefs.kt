package org.beidou.stareye.feature.yuheng

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * 玉衡中枢配置持久化。
 */
object YuhengPrefs {

    private const val PREFS = "yuheng_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 是否显示危险功能（擦除数据、重启等） */
    fun showDangerous(context: Context): Boolean = prefs(context).getBoolean("show_dangerous", false)

    fun setShowDangerous(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean("show_dangerous", v).apply()
    }

    /** 对外 API 是否启用 */
    fun apiEnabled(context: Context): Boolean = prefs(context).getBoolean("api_enabled", false)

    /** 对外 API 密钥 */
    fun apiKey(context: Context): String = prefs(context).getString("api_key", "") ?: ""

    fun setApi(context: Context, enabled: Boolean, key: String) {
        prefs(context).edit().putBoolean("api_enabled", enabled).putString("api_key", key).apply()
    }

    /** 生成随机密钥（16 字节 → 无填充 Base64） */
    fun generateKey(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * 常量时间比较密钥，避免时序侧信道。
     *
     * 说明：OwnDroid 原实现用的是普通 `String.equals`，这里做了加强。
     */
    fun verifyKey(context: Context, provided: String?): Boolean {
        if (!apiEnabled(context)) return false
        val expected = apiKey(context)
        if (expected.isEmpty() || provided.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            provided.toByteArray(Charsets.UTF_8),
        )
    }

    // ── 网络日志（本地留存，供导出） ──

    fun appendNetworkLog(context: Context, line: String) {
        val p = prefs(context)
        val cur = p.getString("net_log", "") ?: ""
        val next = (cur + line + "\n").takeLast(200_000)
        p.edit().putString("net_log", next).apply()
    }

    fun networkLog(context: Context): String = prefs(context).getString("net_log", "") ?: ""

    fun clearNetworkLog(context: Context) {
        prefs(context).edit().remove("net_log").apply()
    }
}
