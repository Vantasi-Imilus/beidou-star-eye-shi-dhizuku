package org.beidou.stareye.feature.yaoguang

import android.app.admin.DevicePolicyManager
import android.content.Context
import org.beidou.stareye.core.shell.ShizukuShell
import org.beidou.stareye.feature.kaiyang.KaiyangState

/** 冻结语义 —— 应用「被冻住」的方式与强度 */
enum class FreezeKind(val label: String, val detail: String) {
    SUSPEND("挂起", "系统级挂起：图标变灰、点击提示已挂起，数据完整保留"),
    HIDE("隐藏", "从桌面与应用列表彻底隐藏，等同卸载但数据仍在，可随时取消隐藏"),
    DISABLE("禁用", "禁用应用组件：图标消失、无法启动，重新启用即恢复"),
    STOP("强停", "仅结束当前进程，下次点击即可正常启动（最温和）"),
}

/** 执行通道 —— 冻结动作以何种特权身份执行 */
enum class FreezeChannel(val label: String, val detail: String) {
    AUTO("自动", "按 开阳 → 天枢 → Root 顺序选用首个可用者"),
    OWNER("开阳（设备所有者）", "本应用已是设备所有者，直接调用 DevicePolicyManager"),
    SHIZUKU("天枢（Shizuku）", "以 ADB / Root 身份执行 pm / am 命令"),
    ROOT("Root（su）", "直接调用本机 su 提权"),
}

/**
 * 瑶光中枢 —— 冻结通道层（自研实现）。
 *
 * 四种冻结语义经统一入口下发到可用通道：
 *  · 开阳（Device Owner）→ DevicePolicyManager 的 挂起 / 隐藏（系统公开 API）
 *  · 天枢（Shizuku）/ Root → pm / am 命令
 * 强停与禁用没有对应的 DPM 公开 API，只能由 shell 类通道执行 —— [supports] 负责该约束。
 */
object YaoguangCore {

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    // ── 通道探测 ──

    /** 通道本身是否就绪（不含语义约束） */
    fun ready(context: Context, channel: FreezeChannel): Boolean = when (channel) {
        FreezeChannel.AUTO ->
            FreezeChannel.entries.any { it != FreezeChannel.AUTO && ready(context, it) }
        FreezeChannel.OWNER -> KaiyangState.isDeviceOwner(context)
        FreezeChannel.SHIZUKU -> ShizukuShell.ready()
        FreezeChannel.ROOT -> rootReady()
    }

    /** 该通道能否执行指定语义（开阳仅有 DPM 的挂起 / 隐藏两项能力） */
    fun supports(channel: FreezeChannel, kind: FreezeKind): Boolean = when (channel) {
        FreezeChannel.OWNER -> kind == FreezeKind.SUSPEND || kind == FreezeKind.HIDE
        FreezeChannel.AUTO, FreezeChannel.SHIZUKU, FreezeChannel.ROOT -> true
    }

    /** 解析真正执行的通道；无可用者返回 null */
    fun resolve(context: Context, channel: FreezeChannel, kind: FreezeKind): FreezeChannel? = when (channel) {
        FreezeChannel.AUTO ->
            listOf(FreezeChannel.OWNER, FreezeChannel.SHIZUKU, FreezeChannel.ROOT)
                .firstOrNull { supports(it, kind) && ready(context, it) }
        else -> channel.takeIf { supports(it, kind) && ready(context, it) }
    }

    /** 解析可执行 shell 命令的通道（卸载 / 重装 / 后台限制等无 DPM 能力的操作） */
    fun resolveShell(context: Context, channel: FreezeChannel): FreezeChannel? = when (channel) {
        FreezeChannel.OWNER -> null
        FreezeChannel.SHIZUKU -> FreezeChannel.SHIZUKU.takeIf { ready(context, it) }
        FreezeChannel.ROOT -> FreezeChannel.ROOT.takeIf { ready(context, it) }
        FreezeChannel.AUTO ->
            listOf(FreezeChannel.SHIZUKU, FreezeChannel.ROOT).firstOrNull { ready(context, it) }
    }

    /** 用户可见的执行描述，如「开阳（设备所有者）· 挂起」 */
    fun describe(context: Context, channel: FreezeChannel, kind: FreezeKind): String {
        val backend = resolve(context, channel, kind) ?: return "无可用通道"
        return "${backend.label} · ${kind.label}"
    }

    // ── 冻结 / 解冻 ──

    fun setFrozen(
        context: Context,
        packageName: String,
        frozen: Boolean,
        channel: FreezeChannel,
        kind: FreezeKind,
    ): Result<Unit> {
        val backend = resolve(context, channel, kind) ?: return Result.failure(
            IllegalStateException("无可用的执行通道，请先激活开阳 / 天枢 / Root 之一")
        )
        return when (backend) {
            FreezeChannel.OWNER -> ownerSet(context, packageName, frozen, kind)
            FreezeChannel.SHIZUKU, FreezeChannel.ROOT -> shellSet(backend, packageName, frozen, kind)
            FreezeChannel.AUTO -> Result.failure(IllegalStateException("通道解析异常"))
        }
    }

    private fun ownerSet(context: Context, pkg: String, frozen: Boolean, kind: FreezeKind): Result<Unit> =
        runCatching {
            val manager = dpm(context)
            val admin = KaiyangState.admin(context)
            when (kind) {
                FreezeKind.SUSPEND -> {
                    val failed = manager.setPackagesSuspended(admin, arrayOf(pkg), frozen)
                    if (failed.isNotEmpty()) error("系统拒绝了挂起请求")
                }

                FreezeKind.HIDE ->
                    if (!manager.setApplicationHidden(admin, pkg, frozen)) error("系统拒绝了隐藏请求")

                else -> error("开阳通道不支持${kind.label}，请改用天枢或 Root")
            }
        }

    private fun shellSet(backend: FreezeChannel, pkg: String, frozen: Boolean, kind: FreezeKind): Result<Unit> {
        // 强停没有「解冻」动作 —— 用户下次启动应用即自动恢复
        if (kind == FreezeKind.STOP && !frozen) return Result.success(Unit)
        return runShell(backend, shellCommand(pkg, frozen, kind)).map { }
    }

    private fun shellCommand(pkg: String, frozen: Boolean, kind: FreezeKind): String {
        val target = safePackage(pkg)
        return when (kind) {
            FreezeKind.SUSPEND ->
                "pm ${if (frozen) "suspend" else "unsuspend"} --user 0 $target"

            FreezeKind.HIDE ->
                "pm ${if (frozen) "hide" else "unhide"} --user 0 $target"

            FreezeKind.DISABLE ->
                if (frozen) "pm disable-user --user 0 $target" else "pm enable --user 0 $target"

            FreezeKind.STOP -> "am force-stop --user 0 $target"
        }
    }

    // ── 其它特权操作 ──

    /** 锁屏：开阳可用时走 DPM.lockNow，否则注入电源键事件 */
    fun lockScreen(context: Context, channel: FreezeChannel): Result<Unit> {
        val ownerPreferred = channel != FreezeChannel.SHIZUKU && channel != FreezeChannel.ROOT
        if (ownerPreferred && KaiyangState.isDeviceOwner(context)) {
            return runCatching { dpm(context).lockNow() }
        }
        val backend = resolveShell(context, channel)
            ?: return Result.failure(IllegalStateException("锁屏需要开阳 / 天枢 / Root 之一"))
        return runShell(backend, "input keyevent KEYCODE_POWER").map { }
    }

    fun uninstall(context: Context, pkg: String, channel: FreezeChannel): Result<Unit> {
        val backend = resolveShell(context, channel)
            ?: return Result.failure(IllegalStateException("卸载需要天枢或 Root 通道"))
        return runShell(backend, "pm uninstall --user 0 ${safePackage(pkg)}").map { }
    }

    fun reinstall(context: Context, pkg: String, channel: FreezeChannel): Result<Unit> {
        val backend = resolveShell(context, channel)
            ?: return Result.failure(IllegalStateException("恢复安装需要天枢或 Root 通道"))
        return runShell(backend, "pm install-existing --user 0 ${safePackage(pkg)}").map { }
    }

    /** 后台运行限制（appops run_any_in_background）：挂起后可选的加固 */
    fun setBackgroundRestricted(
        context: Context,
        pkg: String,
        restricted: Boolean,
        channel: FreezeChannel,
    ): Result<Unit> {
        val backend = resolveShell(context, channel)
            ?: return Result.failure(IllegalStateException("后台限制需要天枢或 Root 通道"))
        val mode = if (restricted) "ignore" else "allow"
        return runShell(backend, "appops set --user 0 ${safePackage(pkg)} RUN_ANY_IN_BACKGROUND $mode").map { }
    }

    // ── shell 执行 ──

    private fun runShell(backend: FreezeChannel, command: String): Result<String> = when (backend) {
        FreezeChannel.ROOT -> runAsRoot(command)
        else -> ShizukuShell.run(command)
    }

    private fun rootReady(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        process.exitValue() == 0 && out.contains("uid=0")
    } catch (_: Throwable) {
        false
    }

    private fun runAsRoot(command: String): Result<String> = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        if (process.exitValue() != 0) {
            error(err.ifBlank { out }.trim().ifBlank { "命令退出码 ${process.exitValue()}" })
        }
        out
    }

    /** 包名白名单校验：命令拼接前必经（包名来自系统，仍防注入） */
    private fun safePackage(pkg: String): String {
        require(pkg.matches(Regex("[A-Za-z0-9._]+"))) { "非法包名：$pkg" }
        return pkg
    }
}
