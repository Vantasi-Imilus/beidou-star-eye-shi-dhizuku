package org.beidou.stareye.feature.tianji

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 批量安装条目状态 */
enum class TianjiBatchStatus {
    /** 待安装 */
    PENDING,

    /** 安装中 */
    INSTALLING,

    /** 安装成功 */
    SUCCESS,

    /** 安装失败（含入队解析失败） */
    FAILED,

    /** 被黑名单拦截 */
    BLOCKED,
}

/** 批量安装队列条目 */
data class TianjiBatchItem(
    val id: Int,
    /** 原始来源（content:// 或 file://），仅用于追溯 */
    val source: Uri,
    /** 文件名（解析失败时也用于展示） */
    val fileName: String,
    /** 解析出的安装包信息；解析失败时为空 */
    val apk: TianjiApk? = null,
    val status: TianjiBatchStatus = TianjiBatchStatus.PENDING,
    /** 失败原因或拦截说明 */
    val error: String? = null,
) {
    val label: String get() = apk?.label ?: fileName
    val packageName: String get() = apk?.packageName.orEmpty()
    val sizeBytes: Long get() = apk?.totalBytes ?: 0L
}

/** 批量任务整体状态（供 UI 订阅） */
data class TianjiBatchState(
    val items: List<TianjiBatchItem> = emptyList(),
    val running: Boolean = false,
    val finished: Boolean = false,
    val aborted: Boolean = false,
    /** 已处理条目数（成功 + 失败 + 拦截） */
    val doneCount: Int = 0,
    /** 当前阶段说明 */
    val stage: String = "",
) {
    val successCount: Int get() = items.count { it.status == TianjiBatchStatus.SUCCESS }
    val failureCount: Int get() = items.count {
        it.status == TianjiBatchStatus.FAILED || it.status == TianjiBatchStatus.BLOCKED
    }
    val pendingCount: Int get() = items.count { it.status == TianjiBatchStatus.PENDING }
}

/**
 * 天玑中枢 —— 批量安装编排执行器（自研实现）。
 *
 * 设计要点：
 *  · **不改安装引擎**：串行逐个调用现有 [TianjiInstaller.install]，单包安装逻辑零改动；
 *  · **中止语义**：置位中止标记后，当前项照常完成，随后不再开始新的条目；
 *  · **黑名单双重拦截**：入队时（[prepareBatchItem]）与执行前各查一次 [TianjiPrefs.isBlocked]，
 *    执行前的复查可拦截入队后新增的黑名单条目；
 *  · **安装记录**：每次安装照常写入 [TianjiLog]，与单包流程一致；
 *  · **线程安全**：状态经 [MutableStateFlow] 原子更新（`update` 为 CAS），可安全地从
 *    IO 线程与 UI 线程同时读写。
 *
 * 生命周期跟随传入的 [CoroutineScope]（页面 `rememberCoroutineScope`）：
 * 离开页面即取消剩余任务。
 */
class TianjiBatchExecutor(
    private val context: Context,
    initialItems: List<TianjiBatchItem>,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(TianjiBatchState(items = initialItems))
    val state: StateFlow<TianjiBatchState> = _state.asStateFlow()

    @Volatile
    private var abortRequested = false

    private var job: Job? = null

    /** 启动批量执行（幂等：运行中重复调用无效；中止后可继续剩余条目） */
    fun start() {
        if (job?.isActive == true) return
        abortRequested = false
        job = scope.launch(Dispatchers.IO) { runLoop() }
    }

    /** 请求中止：当前项完成后停止后续条目 */
    fun abort() {
        abortRequested = true
        update { it.copy(stage = "中止中：当前项完成后停止…") }
    }

    /** 串行主循环：每次取一个「待安装」条目执行，直到队列清空或收到中止 */
    private fun runLoop() {
        update { it.copy(running = true, finished = false, aborted = false, stage = "开始批量安装…") }
        try {
            while (true) {
                val next = _state.value.items.firstOrNull { it.status == TianjiBatchStatus.PENDING }
                    ?: break
                if (abortRequested) break
                process(next)
            }
        } finally {
            update {
                it.copy(
                    running = false,
                    finished = true,
                    aborted = abortRequested,
                    stage = if (abortRequested) "已中止" else "批量任务全部完成",
                )
            }
        }
    }

    /** 执行单个条目：解析失败 / 黑名单 / 安装结果都会落到条目状态并写安装记录 */
    private fun process(item: TianjiBatchItem) {
        val apk = item.apk
        if (apk == null) {
            mark(item.id, TianjiBatchStatus.FAILED, item.error ?: "解析失败")
            return
        }

        // 执行前复查黑名单（入队后名单可能新增了该包）
        if (TianjiPrefs.isBlocked(context, apk.packageName)) {
            mark(item.id, TianjiBatchStatus.BLOCKED, "「${apk.packageName}」在黑名单中，已禁止安装")
            return
        }

        mark(item.id, TianjiBatchStatus.INSTALLING)
        val result = runCatching {
            TianjiInstaller.install(context, apk, apk.splitFiles) { s ->
                update { it.copy(stage = "${item.label}：$s") }
            }
        }.getOrElse { Result.failure(it) }

        val detail = result.fold(
            onSuccess = { it.ifBlank { "成功" } },
            onFailure = { "失败：${it.message ?: "未知错误"}" },
        )
        // 每次安装照常写入安装记录
        TianjiLog.add(
            context,
            TianjiLogEntry(System.currentTimeMillis(), "安装", apk.packageName, apk.label, result.isSuccess, detail),
        )
        mark(
            item.id,
            if (result.isSuccess) TianjiBatchStatus.SUCCESS else TianjiBatchStatus.FAILED,
            if (result.isSuccess) null else detail,
        )
    }

    /** 原子更新整体状态 */
    private fun update(transform: (TianjiBatchState) -> TianjiBatchState) {
        _state.update { s ->
            val next = transform(s)
            next.copy(doneCount = next.items.count {
                it.status == TianjiBatchStatus.SUCCESS ||
                    it.status == TianjiBatchStatus.FAILED ||
                    it.status == TianjiBatchStatus.BLOCKED
            })
        }
    }

    /** 更新单条目状态 */
    private fun mark(id: Int, status: TianjiBatchStatus, error: String? = null) {
        update { s ->
            s.copy(items = s.items.map {
                if (it.id == id) it.copy(status = status, error = error) else it
            })
        }
    }
}

/**
 * 入队准备：复制到缓存 → 解析 → 黑名单初筛。
 *
 * 解析产物会被搬入本条目专属缓存目录（见 [relocateApk]）——因为
 * [copyToCache] 与 [TianjiAnalyzer.extractSplits] 各自重建共享缓存目录，
 * 若不搬迁，处理下一个条目时本条目已解析的文件会被清空。
 */
internal fun prepareBatchItem(context: Context, index: Int, uri: Uri): TianjiBatchItem =
    runCatching {
        val file = copyToCache(context, uri)
        val apk = TianjiAnalyzer.analyze(context, file).getOrThrow()
        if (TianjiPrefs.isBlocked(context, apk.packageName)) {
            // 入队时即拦截，后续执行前还会复查
            TianjiBatchItem(
                id = index,
                source = uri,
                fileName = file.name,
                apk = apk,
                status = TianjiBatchStatus.BLOCKED,
                error = "「${apk.packageName}」在黑名单中，已禁止安装",
            )
        } else {
            TianjiBatchItem(
                id = index,
                source = uri,
                fileName = file.name,
                apk = relocateApk(context, apk),
            )
        }
    }.getOrElse { e ->
        TianjiBatchItem(
            id = index,
            source = uri,
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "package_$index",
            status = TianjiBatchStatus.FAILED,
            error = "解析失败：${e.message ?: "未知错误"}",
        )
    }

/**
 * 把解析产物（主包 + 分片）复制到本条目专属缓存目录并重建 [TianjiApk]。
 * 目录名以纳秒时间戳区分，不存在路径冲突；复制出的文件名沿用解析器
 * 已经过安全清洗的名称（见 [copyToCache]）。
 */
private fun relocateApk(context: Context, apk: TianjiApk): TianjiApk {
    val dir = File(context.cacheDir, "tianji_batch_${System.nanoTime()}").apply { mkdirs() }
    val base = File(dir, apk.file.name).also { out -> apk.file.copyTo(out, overwrite = true) }
    val splits = apk.splitFiles.map { f ->
        File(dir, f.name).also { out -> f.copyTo(out, overwrite = true) }
    }
    return apk.copy(file = base, splitFiles = splits)
}
