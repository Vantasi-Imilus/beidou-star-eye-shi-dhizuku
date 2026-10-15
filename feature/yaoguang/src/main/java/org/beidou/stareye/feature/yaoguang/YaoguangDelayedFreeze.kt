package org.beidou.stareye.feature.yaoguang

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import kotlin.concurrent.thread

/**
 * 延时冻结（持久化调度）。
 *
 * 计划必须在进程被系统回收后仍然生效，故不使用进程内定时器：
 * 任务持久化到 SharedPreferences，由 AlarmManager 持有一次性闹钟，
 * 到期经 [YaoguangDelayedFreezeReceiver] 执行。设备重启会清空闹钟，
 * 由 [YaoguangBootReceiver] 调 [reschedule] 重排。
 */
object YaoguangDelayedFreeze {

    /** 一条待执行的延时冻结 */
    data class Task(val packageName: String, val atMillis: Long)

    private const val PREFS = "yaoguang_prefs"
    private const val KEY = "delayed_freezes"
    private const val REQUEST_CODE = 2001

    private val gson = Gson()
    private val TYPE: Type = TypeToken.getParameterized(List::class.java, Task::class.java).type

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 全部待执行任务（按到期时间升序） */
    fun pending(context: Context): List<Task> = try {
        prefs(context).getString(KEY, null)
            ?.let { gson.fromJson<List<Task>>(it, TYPE) }
            ?.sortedBy { it.atMillis }
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun write(context: Context, tasks: List<Task>) {
        prefs(context).edit().putString(KEY, gson.toJson(tasks)).apply()
    }

    /** 排定一条延时冻结（同包已有任务则覆盖） */
    fun schedule(context: Context, packageName: String, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        write(context, pending(context).filterNot { it.packageName == packageName } + Task(packageName, at))
        armNext(context)
    }

    /**
     * 设备重启后重排 —— 闹钟不跨重启存活，开机接收器需调用本方法恢复计划。
     */
    fun reschedule(context: Context) {
        armNext(context)
    }

    /** 为「最早到期」的任务设置一次性闹钟；无任务则取消闹钟 */
    private fun armNext(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = pending(context).minByOrNull { it.atMillis }
        if (next == null) {
            manager.cancel(intentOf(context))
            return
        }
        // setAndAllowWhileIdle：无需精确闹钟权限，Doze 下仍会触发（允许系统小幅推迟）
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, intentOf(context))
    }

    private fun intentOf(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, YaoguangDelayedFreezeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 闹钟触发（或开机重排后到点）：执行全部已到期任务，未到期的保留并重排 */
    fun fire(context: Context) {
        val now = System.currentTimeMillis()
        // 容差 5 秒：闹钟可能因系统调度比设定时刻略早
        val (due, later) = pending(context).partition { it.atMillis <= now + 5_000L }
        write(context, later)
        armNext(context)
        due.forEach { task ->
            YaoguangOps.setFrozen(context, task.packageName, true)
        }
    }
}

/**
 * 延时冻结到期闹钟的接收器。
 *
 * [fire] 内的冻结是同步 shell 调用，需要时间，故用 goAsync 把生命周期
 * 交给后台线程，避免 onReceive 返回后进程被回收。
 */
class YaoguangDelayedFreezeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        thread(name = "yaoguang-delayed-freeze") {
            try {
                YaoguangDelayedFreeze.fire(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
