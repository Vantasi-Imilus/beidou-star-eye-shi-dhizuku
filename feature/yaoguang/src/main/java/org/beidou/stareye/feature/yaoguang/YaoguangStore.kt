package org.beidou.stareye.feature.yaoguang

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.security.MessageDigest
import java.security.SecureRandom

/** 默认标签 id，不可删除（顶层常量：清单条目的默认参数需要它） */
const val DEFAULT_TAG = 0

/** 清单条目：纳入「我的清单」的应用及其标记 */
data class YaoguangItem(
    val packageName: String,
    val tagIds: List<Int> = listOf(DEFAULT_TAG),
    val pinned: Boolean = false,
    val whitelisted: Boolean = false,
)

/** 标签（稳定 id + 名称） */
data class YaoguangTag(val id: Int, val name: String)

/** 操作记录 */
data class YaoguangLog(val time: Long, val action: String, val target: String, val result: String)

/**
 * 瑶光中枢持久化：清单 / 标签 / 设置 / 操作记录。
 * SharedPreferences + JSON（与天璇 / 天玑 / 玉衡同一存储范式，不引入数据库）。
 */
object YaoguangStore {

    /** 操作记录上限 */
    private const val MAX_LOGS = 300

    private const val PREFS = "yaoguang_prefs"

    /** 雹兼容契约的载体组件（manifest 中的 activity-alias，默认禁用） */
    private const val HAIL_COMPAT_COMPONENT =
        "org.beidou.stareye.feature.yaoguang.YaoguangHailCompatActivity"
    private const val K_ITEMS = "items"
    private const val K_TAGS = "tags"
    private const val K_LOGS = "logs"
    private const val K_KIND = "freeze_kind"
    private const val K_CHANNEL = "freeze_channel"
    private const val K_AUTO = "auto_freeze"
    private const val K_AUTO_DELAY = "auto_freeze_delay"
    private const val K_SKIP_CHARGING = "skip_charging"
    private const val K_SKIP_FOREGROUND = "skip_foreground"
    private const val K_SKIP_NOTIFYING = "skip_notifying"
    private const val K_TILE_ACTION = "tile_action"
    private const val K_SHORTCUT_ACTION = "shortcut_action"
    private const val K_API_ENABLED = "api_enabled"
    private const val K_API_HAIL_COMPAT = "api_hail_compat"
    private const val K_API_KEY = "api_key"
    private const val K_AUTO_FROZEN = "auto_frozen"
    private const val K_FUZZY = "fuzzy_search"
    private const val K_NINE_KEY = "nine_key_search"

    private val gson = Gson()
    private val ITEM_TYPE: Type =
        TypeToken.getParameterized(List::class.java, YaoguangItem::class.java).type
    private val TAG_TYPE: Type =
        TypeToken.getParameterized(List::class.java, YaoguangTag::class.java).type
    private val LOG_TYPE: Type =
        TypeToken.getParameterized(List::class.java, YaoguangLog::class.java).type
    private val STRING_TYPE: Type =
        TypeToken.getParameterized(List::class.java, String::class.java).type

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun <T> readList(context: Context, key: String, type: Type): List<T> = try {
        prefs(context).getString(key, null)?.let { gson.fromJson<List<T>>(it, type) } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun writeList(context: Context, key: String, list: List<*>) {
        prefs(context).edit().putString(key, gson.toJson(list)).apply()
    }

    // ── 清单 ──

    fun items(context: Context): List<YaoguangItem> = readList(context, K_ITEMS, ITEM_TYPE)

    fun isChecked(context: Context, packageName: String): Boolean =
        items(context).any { it.packageName == packageName }

    fun add(context: Context, packageName: String, tagId: Int = DEFAULT_TAG) {
        val list = items(context).toMutableList()
        if (list.any { it.packageName == packageName }) return
        list += YaoguangItem(packageName, tagIds = listOf(tagId))
        writeList(context, K_ITEMS, list)
    }

    fun addAll(context: Context, packageNames: Collection<String>, tagId: Int = DEFAULT_TAG) {
        val list = items(context).toMutableList()
        packageNames.filterNot { pkg -> list.any { it.packageName == pkg } }
            .forEach { list += YaoguangItem(it, tagIds = listOf(tagId)) }
        writeList(context, K_ITEMS, list)
    }

    fun remove(context: Context, packageNames: Collection<String>) {
        writeList(context, K_ITEMS, items(context).filterNot { it.packageName in packageNames })
    }

    private fun update(context: Context, packageName: String, transform: (YaoguangItem) -> YaoguangItem) {
        writeList(
            context, K_ITEMS,
            items(context).map { if (it.packageName == packageName) transform(it) else it }
        )
    }

    fun setPinned(context: Context, packageName: String, pinned: Boolean) =
        update(context, packageName) { it.copy(pinned = pinned) }

    fun setWhitelisted(context: Context, packageName: String, whitelisted: Boolean) =
        update(context, packageName) { it.copy(whitelisted = whitelisted) }

    /** 批量为应用附加 / 移除某标签；至少保留默认标签，避免条目在首页消失 */
    fun setTag(context: Context, packageNames: Collection<String>, tagId: Int, attached: Boolean) {
        writeList(
            context, K_ITEMS,
            items(context).map { item ->
                if (item.packageName !in packageNames) item
                else {
                    val tags = item.tagIds.toMutableSet()
                    if (attached) tags += tagId else tags -= tagId
                    if (tags.isEmpty()) tags += DEFAULT_TAG
                    item.copy(tagIds = tags.sorted())
                }
            }
        )
    }

    // ── 标签 ──

    /** 标签表（确保默认标签存在） */
    fun tags(context: Context): List<YaoguangTag> {
        val list = readList<YaoguangTag>(context, K_TAGS, TAG_TYPE)
        return if (list.any { it.id == DEFAULT_TAG }) list
        else listOf(YaoguangTag(DEFAULT_TAG, "默认")) + list
    }

    fun addTag(context: Context, name: String): YaoguangTag {
        val list = tags(context).toMutableList()
        val tag = YaoguangTag((list.maxOfOrNull { it.id } ?: DEFAULT_TAG) + 1, name)
        list += tag
        writeList(context, K_TAGS, list)
        return tag
    }

    fun renameTag(context: Context, id: Int, name: String) {
        writeList(context, K_TAGS, tags(context).map { if (it.id == id) it.copy(name = name) else it })
    }

    fun removeTag(context: Context, id: Int) {
        if (id == DEFAULT_TAG) return
        writeList(context, K_TAGS, tags(context).filterNot { it.id == id })
        // 同步从条目中摘除该标签（条目本身保留）
        writeList(
            context, K_ITEMS,
            items(context).map { item ->
                item.copy(tagIds = item.tagIds.filterNot { it == id }.ifEmpty { listOf(DEFAULT_TAG) })
            }
        )
    }

    // ── 设置 ──

    fun kind(context: Context): FreezeKind =
        runCatching { FreezeKind.valueOf(prefs(context).getString(K_KIND, "") ?: "") }
            .getOrDefault(FreezeKind.SUSPEND)

    fun setKind(context: Context, kind: FreezeKind) {
        prefs(context).edit().putString(K_KIND, kind.name).apply()
    }

    fun channel(context: Context): FreezeChannel =
        runCatching { FreezeChannel.valueOf(prefs(context).getString(K_CHANNEL, "") ?: "") }
            .getOrDefault(FreezeChannel.AUTO)

    fun setChannel(context: Context, channel: FreezeChannel) {
        prefs(context).edit().putString(K_CHANNEL, channel.name).apply()
    }

    fun autoFreeze(context: Context): Boolean = prefs(context).getBoolean(K_AUTO, false)

    fun setAutoFreeze(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_AUTO, enabled).apply()
    }

    /** 息屏后延迟多少分钟执行自动冻结（0 = 立即） */
    fun autoFreezeDelay(context: Context): Int = prefs(context).getInt(K_AUTO_DELAY, 0)

    fun setAutoFreezeDelay(context: Context, minutes: Int) {
        prefs(context).edit().putInt(K_AUTO_DELAY, minutes.coerceIn(0, 60)).apply()
    }

    fun skipWhileCharging(context: Context): Boolean = prefs(context).getBoolean(K_SKIP_CHARGING, true)

    fun setSkipWhileCharging(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_SKIP_CHARGING, value).apply()
    }

    fun skipForegroundApp(context: Context): Boolean = prefs(context).getBoolean(K_SKIP_FOREGROUND, false)

    fun setSkipForegroundApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_SKIP_FOREGROUND, value).apply()
    }

    fun skipNotifyingApp(context: Context): Boolean = prefs(context).getBoolean(K_SKIP_NOTIFYING, false)

    fun setSkipNotifyingApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_SKIP_NOTIFYING, value).apply()
    }

    /** 模糊搜索（字符按序包含） */
    fun fuzzySearch(context: Context): Boolean = prefs(context).getBoolean(K_FUZZY, false)

    fun setFuzzySearch(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_FUZZY, value).apply()
    }

    /** 九键搜索（纯数字查询按 T9 映射匹配） */
    fun nineKeySearch(context: Context): Boolean = prefs(context).getBoolean(K_NINE_KEY, false)

    fun setNineKeySearch(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_NINE_KEY, value).apply()
    }

    /** 磁贴动作（取值见 YaoguangShortcuts.ACTIONS） */
    fun tileAction(context: Context): String =
        prefs(context).getString(K_TILE_ACTION, YaoguangShortcuts.ACTION_AUTO_FREEZE) ?: YaoguangShortcuts.ACTION_AUTO_FREEZE

    fun setTileAction(context: Context, action: String) {
        prefs(context).edit().putString(K_TILE_ACTION, action).apply()
    }

    /** 动态快捷方式动作 */
    fun dynamicShortcutAction(context: Context): String =
        prefs(context).getString(K_SHORTCUT_ACTION, YaoguangShortcuts.ACTION_NONE) ?: YaoguangShortcuts.ACTION_NONE

    fun setDynamicShortcutAction(context: Context, action: String) {
        prefs(context).edit().putString(K_SHORTCUT_ACTION, action).apply()
    }

    // ── 对外 API ──

    /** 对外 API 总开关（默认开启，与雹原版一样开箱可用） */
    fun apiEnabled(context: Context): Boolean = prefs(context).getBoolean(K_API_ENABLED, true)

    fun setApiEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_API_ENABLED, enabled).apply()
    }

    /**
     * 是否接受雹（com.aistra.hail.action.*）原版契约。
     *
     * 默认关闭：设备上可能同时装有雹原版，同名 action 会让系统弹出选择器；
     * 需要迁移既有脚本的用户在设置中显式开启即可。
     */
    fun hailCompat(context: Context): Boolean = prefs(context).getBoolean(K_API_HAIL_COMPAT, false)

    fun setHailCompat(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_API_HAIL_COMPAT, enabled).apply()
        syncHailCompat(context)
    }

    /** 让别名组件的启用状态与设置一致：关闭时该组件不参与意图解析 */
    fun syncHailCompat(context: Context) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, HAIL_COMPAT_COMPONENT),
                if (hailCompat(context)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    fun apiKey(context: Context): String = prefs(context).getString(K_API_KEY, "") ?: ""

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(K_API_KEY, key).apply()
    }

    /** 生成随机密钥（16 字节 → 无填充 Base64） */
    fun generateApiKey(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * 校验外部调用凭据：未启用 API 直接拒绝；未设置密钥时放行（保持雹式免密钥契约），
     * 设置后必须匹配 —— 常量时间比较，避免时序侧信道。
     */
    fun verifyApi(context: Context, provided: String?): Boolean {
        if (!apiEnabled(context)) return false
        val expected = apiKey(context)
        if (expected.isEmpty()) return true
        return provided != null && MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            provided.toByteArray(Charsets.UTF_8),
        )
    }

    // ── 自动冻结记录（亮屏时只解冻这些，不影响用户手动冻结的应用） ──

    fun autoFrozen(context: Context): Set<String> =
        readList<String>(context, K_AUTO_FROZEN, STRING_TYPE).toSet()

    fun setAutoFrozen(context: Context, packageNames: Collection<String>) {
        writeList(context, K_AUTO_FROZEN, packageNames.toList())
    }

    // ── 操作记录 ──

    fun logs(context: Context): List<YaoguangLog> = readList(context, K_LOGS, LOG_TYPE)

    fun appendLog(context: Context, action: String, target: String, result: String) {
        val list = (listOf(YaoguangLog(System.currentTimeMillis(), action, target, result)) + logs(context))
            .take(MAX_LOGS)
        writeList(context, K_LOGS, list)
    }

    fun clearLogs(context: Context) {
        prefs(context).edit().remove(K_LOGS).apply()
    }
}
