package org.beidou.stareye.feature.tianji

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 一条天玑中枢安装记录。
 */
data class TianjiLogEntry(
    val time: Long,
    val action: String,
    val packageName: String,
    val label: String,
    val success: Boolean,
    val detail: String,
)

/**
 * 安装记录持久化（SharedPreferences + JSON，自研实现）。
 */
object TianjiLog {

    private const val PREFS = "tianji_log"
    private const val KEY = "entries"
    private const val MAX = 200

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<TianjiLogEntry> {
        val json = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<MutableList<TianjiLogEntry>>() {}.type
            gson.fromJson<MutableList<TianjiLogEntry>>(json, type) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, entry: TianjiLogEntry) {
        val list = (all(context).toMutableList() + entry).takeLast(MAX)
        prefs(context).edit().putString(KEY, gson.toJson(list)).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
