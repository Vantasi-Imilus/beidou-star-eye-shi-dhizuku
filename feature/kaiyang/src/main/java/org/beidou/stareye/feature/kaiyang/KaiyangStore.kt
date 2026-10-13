package org.beidou.stareye.feature.kaiyang

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 已授权应用记录（对应原版 Dhizuku 的 AppEntity）。
 */
data class KaiyangApp(
    var uid: Int = -1,
    var signature: String = "",
    var allowApi: Boolean = false,
    var blocked: Boolean = false,
    var createdAt: Long = 0L,
    var modifiedAt: Long = 0L,
)

/**
 * 开阳中枢的持久化存储（以 SharedPreferences + JSON 替代原版 Room，逻辑等价）。
 */
object KaiyangStore {

    private const val PREFS = "kaiyang_store"
    private const val KEY_APPS = "apps"
    private const val KEY_ENABLED = "dhizuku_enabled"
    private const val KEY_WHITELIST = "whitelist_mode"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): MutableList<KaiyangApp> {
        val json = prefs(context).getString(KEY_APPS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<KaiyangApp>>() {}.type
            gson.fromJson<MutableList<KaiyangApp>>(json, type) ?: mutableListOf()
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun save(context: Context, apps: List<KaiyangApp>) {
        prefs(context).edit().putString(KEY_APPS, gson.toJson(apps)).apply()
    }

    @Synchronized
    fun findByUid(context: Context, uid: Int): KaiyangApp? =
        all(context).firstOrNull { it.uid == uid }

    @Synchronized
    fun upsert(context: Context, app: KaiyangApp) {
        val apps = all(context)
        val index = apps.indexOfFirst { it.uid == app.uid }
        app.modifiedAt = System.currentTimeMillis()
        if (index >= 0) {
            apps[index] = app
        } else {
            app.createdAt = System.currentTimeMillis()
            apps.add(app)
        }
        save(context, apps)
    }

    @Synchronized
    fun delete(context: Context, uid: Int) {
        val apps = all(context)
        apps.removeAll { it.uid == uid }
        save(context, apps)
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isWhitelistMode(context: Context): Boolean = prefs(context).getBoolean(KEY_WHITELIST, false)

    fun setWhitelistMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WHITELIST, enabled).apply()
    }
}
