package org.beidou.stareye.feature.yaoguang

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import com.github.promeg.pinyinhelper.Pinyin
import org.beidou.stareye.core.freeze.FreezeEvents
import org.beidou.stareye.core.freeze.FreezeState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 应用条目（应用页与首页共用） */
data class YaoguangApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** false 表示已被隐藏（存在于清单但当前不可见） */
    val installed: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val icon: Bitmap?,
    val flags: FreezeState.Flags,
    val checked: Boolean,
    val pinned: Boolean,
    val whitelisted: Boolean,
    val tagIds: List<Int>,
) {
    val frozen: Boolean get() = flags.frozen
    val stateLabel: String get() = flags.kind.label
}

/** 批量操作结果 */
data class BatchResult(val done: Int, val failed: Int, val lastError: String? = null) {
    val text: String
        get() = when {
            failed == 0 && done == 0 -> "没有可处理的应用"
            failed == 0 -> "成功 $done 个"
            done == 0 -> "全部失败（$failed 个）：${lastError ?: "未知原因"}"
            else -> "成功 $done 个，失败 $failed 个（${lastError ?: ""}）"
        }
}

/**
 * 瑶光中枢 —— 业务操作层。
 *
 * 冻结 / 解冻统一在此收口：写操作记录 + 广播 [FreezeEvents]，
 * 使天璇 / 玉衡 / 瑶光的界面在任何一处变更后都能同步刷新。
 * 通道与语义由 [YaoguangCore] 按 [YaoguangStore] 中的设置解析。
 */
object YaoguangOps {

    /** 延时冻结的进程内调度器（应用进程退出则计划失效） */
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    // ── 单应用冻结 / 解冻 ──

    fun setFrozen(context: Context, packageName: String, frozen: Boolean): Result<Unit> {
        if (packageName == context.packageName) {
            return Result.failure(IllegalStateException("不能冻结本应用"))
        }
        val kind = YaoguangStore.kind(context)
        val channel = YaoguangStore.channel(context)
        val result = YaoguangCore.setFrozen(context, packageName, frozen, channel, kind)
        YaoguangStore.appendLog(
            context,
            action = if (frozen) "冻结·${kind.label}" else "解冻",
            target = appLabel(context, packageName),
            result = result.fold({ "成功" }, { it.message ?: "失败" }),
        )
        if (result.isSuccess) FreezeEvents.notifyChanged(packageName)
        return result
    }

    fun setFrozenBatch(context: Context, packageNames: Collection<String>, frozen: Boolean): BatchResult {
        var done = 0
        var failed = 0
        var lastError: String? = null
        packageNames.filterNot { it == context.packageName }.forEach { pkg ->
            setFrozen(context, pkg, frozen).fold(
                onSuccess = { done++ },
                onFailure = {
                    failed++
                    lastError = it.message
                },
            )
        }
        return BatchResult(done, failed, lastError)
    }

    // ── 清单级批量（快捷方式 / 磁贴 / 对外 API 的公共动作） ──

    fun freezeAll(context: Context): BatchResult =
        setFrozenBatch(context, YaoguangStore.items(context).map { it.packageName }, true)

    fun unfreezeAll(context: Context): BatchResult =
        setFrozenBatch(context, YaoguangStore.items(context).map { it.packageName }, false)

    fun freezeNonWhitelisted(context: Context): BatchResult =
        setFrozenBatch(
            context,
            YaoguangStore.items(context).filterNot { it.whitelisted }.map { it.packageName },
            true,
        )

    fun setTagFrozen(context: Context, tagId: Int, frozen: Boolean): BatchResult =
        setFrozenBatch(
            context,
            YaoguangStore.items(context).filter { tagId in it.tagIds }.map { it.packageName },
            frozen,
        )

    // ── 启动（冻结中先解冻） ──

    fun launch(context: Context, packageName: String): Result<Unit> {
        val wasFrozen = FreezeState.isFrozen(context, packageName)
        return runCatching {
            if (wasFrozen) setFrozen(context, packageName, false).getOrThrow()
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: error("该应用没有可启动的入口")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (wasFrozen) {
                // 解冻后系统包状态更新略有延迟，稍候再拉起，避免被静默拒绝
                Handler(Looper.getMainLooper()).postDelayed(
                    { runCatching { context.startActivity(intent) } },
                    350,
                )
            } else {
                context.startActivity(intent)
            }
        }
    }

    // ── 延时冻结 ──

    fun scheduleFreeze(context: Context, packageName: String, minutes: Int) {
        val appContext = context.applicationContext
        val label = appLabel(context, packageName)
        scheduler.schedule(
            { runCatching { setFrozen(appContext, packageName, true) } },
            minutes.toLong(),
            TimeUnit.MINUTES,
        )
        YaoguangStore.appendLog(context, "延时冻结", "$label · $minutes 分钟后", "已排定")
    }

    // ── 锁屏 / 卸载 / 重装 / 后台限制 ──

    fun lockScreen(context: Context): Result<Unit> =
        YaoguangCore.lockScreen(context, YaoguangStore.channel(context)).onSuccess {
            YaoguangStore.appendLog(context, "锁屏", "", "成功")
        }

    fun lockAndFreeze(context: Context): BatchResult {
        lockScreen(context)
        return freezeNonWhitelisted(context)
    }

    fun uninstall(context: Context, packageName: String): Result<Unit> {
        val result = YaoguangCore.uninstall(context, packageName, YaoguangStore.channel(context))
        if (result.isSuccess) {
            YaoguangStore.appendLog(context, "卸载", appLabel(context, packageName), "成功")
            FreezeEvents.notifyChanged(packageName)
            return result
        }
        // 无天枢 / Root 通道时回退系统卸载界面
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            YaoguangStore.appendLog(context, "卸载", appLabel(context, packageName), "已交由系统卸载器")
        }
    }

    fun reinstall(context: Context, packageName: String): Result<Unit> {
        val result = YaoguangCore.reinstall(context, packageName, YaoguangStore.channel(context))
        YaoguangStore.appendLog(
            context, "恢复安装", appLabel(context, packageName),
            result.fold({ "成功" }, { it.message ?: "失败" }),
        )
        if (result.isSuccess) FreezeEvents.notifyChanged(packageName)
        return result
    }

    fun setBackgroundRestricted(context: Context, packageName: String, restricted: Boolean): Result<Unit> {
        val result = YaoguangCore.setBackgroundRestricted(
            context, packageName, restricted, YaoguangStore.channel(context),
        )
        YaoguangStore.appendLog(
            context,
            action = if (restricted) "限制后台" else "解除后台限制",
            target = appLabel(context, packageName),
            result = result.fold({ "成功" }, { it.message ?: "失败" }),
        )
        return result
    }

    // ── 自动冻结的跳过条件 ──

    /**
     * 是否在充电。
     *
     * 用电池广播的粘性意图判定，而非 BatteryManager.isCharging ——
     * 后者在部分 ROM（实测华为）恒返回 false，会让「充电时跳过」失效。
     */
    fun isCharging(context: Context): Boolean = try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    } catch (_: Throwable) {
        false
    }

    /** 是否已授予「用量访问」权限（「跳过前台应用」依赖它） */
    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }

    /** 最近 [withinMinutes] 分钟内使用过的应用；无权限时返回空集 */
    fun recentlyUsed(context: Context, withinMinutes: Int): Set<String> = try {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - withinMinutes * 60_000L
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
            ?.filter { it.lastTimeUsed >= begin }
            ?.map { it.packageName }
            ?.toSet()
            ?: emptySet()
    } catch (_: Throwable) {
        emptySet()
    }

    // ── 加载应用 ──

    /** 全部应用（含已隐藏者），带清单标记与冻结状态 */
    fun loadApps(context: Context): List<YaoguangApp> = load(context, onlyChecked = false)

    /** 仅「我的清单」内的应用（首页用） */
    fun loadCheckedApps(context: Context): List<YaoguangApp> = load(context, onlyChecked = true)

    private fun load(context: Context, onlyChecked: Boolean): List<YaoguangApp> {
        val pm = context.packageManager
        val items = YaoguangStore.items(context).associateBy { it.packageName }
        val infos = try {
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (_: Throwable) {
            return emptyList()
        }
        return infos.mapNotNull { ai ->
            val pkg = ai.packageName
            val flags = FreezeState.flagsOf(ai)
            val installed = (ai.flags and ApplicationInfo.FLAG_INSTALLED) != 0
            // MATCH_UNINSTALLED_PACKAGES 会带出「卸载但保留数据」的残留，过滤掉；已隐藏者保留展示
            if (!installed && !flags.hidden) return@mapNotNull null
            val item = items[pkg]
            if (onlyChecked && item == null) return@mapNotNull null

            val info = try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: Throwable) {
                null
            }
            YaoguangApp(
                packageName = pkg,
                label = appLabelOf(ai, pm),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                installed = installed,
                installTime = info?.firstInstallTime ?: 0L,
                updateTime = info?.lastUpdateTime ?: 0L,
                icon = try {
                    ai.loadIcon(pm).toBitmap(96, 96)
                } catch (_: Throwable) {
                    null
                },
                flags = flags,
                checked = item != null,
                pinned = item?.pinned == true,
                whitelisted = item?.whitelisted == true,
                tagIds = item?.tagIds ?: emptyList(),
            )
        }.sortedWith(compareByDescending<YaoguangApp> { it.pinned }.thenBy { it.label.lowercase() })
    }

    fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(
            pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        ).toString()
    } catch (_: Throwable) {
        packageName
    }

    private fun appLabelOf(info: ApplicationInfo, pm: PackageManager): String = try {
        info.loadLabel(pm).toString()
    } catch (_: Throwable) {
        info.packageName
    }

    // ── 搜索匹配 ──

    private class PinyinInfo(val initials: String, val full: String, val nineKey: String)

    private val pinyinCache = ConcurrentHashMap<String, PinyinInfo>()

    private val nineKeyMap = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2', 'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4', 'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6', 'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8', 'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
    )

    /** 名称 / 包名 / 拼音（首字母 + 全拼）/ 九键 / 模糊 匹配 */
    fun matches(app: YaoguangApp, query: String, fuzzy: Boolean, nineKey: Boolean): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (app.label.lowercase().contains(q) || app.packageName.contains(q)) return true

        val py = pinyinOf(app.label)
        if (py.initials.contains(q) || py.full.contains(q)) return true
        if (nineKey) {
            val digits = q.filter { it.isDigit() }
            if (digits.isNotEmpty() && py.nineKey.contains(digits)) return true
        }
        return fuzzy && py.full.length >= q.length && isSubsequence(app.label.lowercase(), q)
    }

    private fun pinyinOf(label: String): PinyinInfo = pinyinCache.getOrPut(label) {
        val full = StringBuilder()
        val initials = StringBuilder()
        label.forEach { ch ->
            when {
                Pinyin.isChinese(ch) -> {
                    val py = Pinyin.toPinyin(ch) ?: ch.toString()
                    initials.append(py.first())
                    full.append(py)
                }

                ch.isLetterOrDigit() -> {
                    initials.append(ch.lowercaseChar())
                    full.append(ch.lowercaseChar())
                }
            }
        }
        val fullText = full.toString()
        PinyinInfo(
            initials = initials.toString(),
            full = fullText,
            nineKey = fullText.map { nineKeyMap[it] ?: it }.joinToString(""),
        )
    }

    /** 查询串的字符按序出现即可（如 ``wchat`` 命中 ``WeChat``） */
    private fun isSubsequence(text: String, query: String): Boolean {
        var i = 0
        for (c in text) {
            if (i < query.length && c == query[i]) i++
            if (i == query.length) return true
        }
        return false
    }
}
