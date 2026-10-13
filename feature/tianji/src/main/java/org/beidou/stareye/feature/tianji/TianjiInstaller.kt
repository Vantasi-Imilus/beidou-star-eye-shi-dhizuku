package org.beidou.stareye.feature.tianji

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

/**
 * 天玑中枢 —— 安装 / 卸载引擎（自研实现）。
 *
 * 安装通道：
 *  ① 特权通道（授权器可选 天枢 Shizuku / 开阳 Dhizuku / Root）→ `pm install-create/-write/-commit` 静默安装
 *  ② 系统安装器回退 → 公开 [PackageInstaller] API，由系统弹窗确认后安装
 *
 * 安装配置（授权器 / 安装原因 / 安装请求方 / 所有用户 / 测试包 / 降级 / 分包选择 / 黑名单）见 [TianjiOptions]。
 */
object TianjiInstaller {

    const val ACTION_INSTALL_RESULT = "org.beidou.stareye.feature.tianji.INSTALL_RESULT"
    const val ACTION_UNINSTALL_RESULT = "org.beidou.stareye.feature.tianji.UNINSTALL_RESULT"
    const val EXTRA_SESSION_ID = "session_id"
    const val EXTRA_PACKAGE_NAME = "package_name"

    /** 当前是否存在可用特权通道 */
    fun privilegedReady(context: Context): Boolean =
        TianjiPrivileged.ready(context, TianjiPrefs.options(context).authorizer)

    // ───────────────────────── 安装 ─────────────────────────

    /**
     * 安装 [TianjiApk]。阻塞调用，须在工作线程执行。
     *
     * @param selectedSplits 选中的分包（不含主包）；为空表示无分包或未选
     * @param onStage 进度回调
     */
    fun install(
        context: Context,
        apk: TianjiApk,
        selectedSplits: List<File> = apk.splitFiles,
        onStage: (String) -> Unit = {},
    ): Result<String> {
        val options = TianjiPrefs.options(context)

        // 黑名单拦截
        if (TianjiPrefs.isBlocked(context, apk.packageName)) {
            return Result.failure(IllegalStateException("「${apk.packageName}」在黑名单中，已禁止安装"))
        }

        val files = listOf(apk.file) + selectedSplits
        return if (TianjiPrivileged.ready(context, options.authorizer)) {
            val chosen = TianjiPrivileged.resolve(context, options.authorizer)
            onStage("经${chosen?.label ?: "特权通道"}静默安装…")
            installViaPrivileged(context, options, files, onStage)
        } else {
            onStage("无可用特权通道，改用系统安装器（需确认）…")
            installViaSystem(context, files)
        }
    }

    /**
     * 经特权通道静默安装：
     * ① 各分片流式上传到 shell 可读的 /data/local/tmp
     * ② `pm install-create`（携带全部安装配置）→ `install-write` ×N → `install-commit`
     */
    private fun installViaPrivileged(
        context: Context,
        options: TianjiOptions,
        files: List<File>,
        onStage: (String) -> Unit,
    ): Result<String> = runCatching {
        val stamp = System.currentTimeMillis()
        val remote = files.mapIndexed { i, _ -> "/data/local/tmp/beidou_$stamp$i.apk" }

        try {
            // 1. 上传分片
            files.forEachIndexed { i, f ->
                onStage("上传分片 ${i + 1}/${files.size}…")
                val p = TianjiPrivileged.start(context, options.authorizer, "cat > ${remote[i]}")
                try {
                    p.outputStream.use { outs -> f.inputStream().use { it.copyTo(outs, 128 * 1024) } }
                    // 排空输出，避免管道阻塞
                    p.inputStream.readBytes()
                    p.errorStream.readBytes()
                    p.waitFor()
                } finally {
                    runCatching { p.destroy() }
                }
                check(p.exitValue() == 0) { "上传失败：${f.name}" }
            }

            // 2. 会话式安装
            onStage("创建安装会话…")
            val total = files.sumOf { it.length() }
            val created = TianjiPrivileged
                .run(context, options.authorizer, "pm install-create ${createArgs(options, total)}")
                .getOrThrow()
            val sessionId = Regex("\\[(\\d+)]").find(created)?.groupValues?.get(1)
                ?: error("无法解析安装会话：${created.trim()}")

            try {
                files.forEachIndexed { i, f ->
                    onStage("写入分片 ${i + 1}/${files.size}…")
                    TianjiPrivileged.run(
                        context,
                        options.authorizer,
                        "pm install-write -S ${f.length()} $sessionId ${shellQuote(f.name)} ${remote[i]}",
                    ).getOrThrow()
                }
                onStage("提交安装…")
                TianjiPrivileged
                    .run(context, options.authorizer, "pm install-commit $sessionId")
                    .getOrThrow().trim()
            } catch (e: Throwable) {
                runCatching {
                    TianjiPrivileged.run(context, options.authorizer, "pm install-abandon $sessionId")
                }
                throw e
            }
        } finally {
            runCatching {
                TianjiPrivileged.run(context, options.authorizer, "rm -f ${remote.joinToString(" ")}")
            }
        }
    }

    /** 由安装配置拼装 `pm install-create` 参数 */
    private fun createArgs(options: TianjiOptions, totalBytes: Long): String = buildString {
        append("-r")                                    // 覆盖安装
        if (options.allowTestPackages) append(" -t")    // 允许测试包
        if (options.allowDowngrade) append(" -d")       // 允许降级
        if (options.installerPackage.isNotBlank()) {
            append(" -i ${shellQuote(options.installerPackage.trim())}")  // 安装请求方
        }
        if (options.forAllUsers) append(" --user all")  // 为所有用户安装
        append(" --install-reason ${options.installReason.code}")
        append(" -S $totalBytes")
    }

    /**
     * 回退通道：公开 [PackageInstaller] API，由系统弹出确认界面。
     */
    private fun installViaSystem(context: Context, files: List<File>): Result<String> = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            files.forEach { f ->
                session.openWrite(f.name, 0, f.length()).use { out ->
                    f.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            val intent = Intent(context, TianjiInstallReceiver::class.java).apply {
                setAction(ACTION_INSTALL_RESULT)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
        "已提交系统安装器"
    }

    // ───────────────────────── 卸载 ─────────────────────────

    /**
     * 卸载 [pkg]。特权通道就绪时静默卸载；否则回退系统卸载界面。
     */
    fun uninstall(context: Context, pkg: String): Result<String> = runCatching {
        val options = TianjiPrefs.options(context)
        if (TianjiPrivileged.ready(context, options.authorizer)) {
            val out = TianjiPrivileged
                .run(context, options.authorizer, "pm uninstall --user 0 ${shellQuote(pkg)}")
                .getOrThrow()
            check(out.contains("Success")) { out.trim().ifBlank { "卸载失败" } }
            out.trim()
        } else {
            // 公开 PackageInstaller.uninstall → 系统卸载界面，不经 intent 分发，避免与本应用转接层回环
            val intent = Intent(context, TianjiInstallReceiver::class.java).apply {
                setAction(ACTION_UNINSTALL_RESULT)
                putExtra(EXTRA_PACKAGE_NAME, pkg)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, pkg.hashCode(), intent, flags)
            context.packageManager.packageInstaller.uninstall(pkg, pending.intentSender)
            "已提交系统卸载器"
        }
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
