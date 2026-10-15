package org.beidou.stareye.feature.tianji

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite

/** SAF 目录内的一个安装包条目 */
data class TianjiSafEntry(
    val uri: Uri,
    val name: String,
    /** 字节数；提供方未给出时为 -1 */
    val size: Long,
)

/**
 * 天玑中枢 —— SAF 目录封装（自研实现）。
 *
 * 通过 `ACTION_OPEN_DOCUMENT_TREE` 选择目录并 `takePersistableUriPermission`
 * 持久化读取权限（SharedPreferences 记住目录 uri），之后用
 * ContentResolver + [DocumentsContract] 直接列举目录内的安装包。
 *
 * 不引入 documentfile 依赖：DocumentsContract 的 child-query 足以满足
 * 「名称 / 大小 / 文档 uri」三要素，少一个依赖面。
 */
object TianjiSafDir {

    private const val PREFS = "tianji_saf"
    private const val KEY_TREE_URI = "tree_uri"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 已记住的目录 uri；从未选择时为空 */
    fun savedTree(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun saveTree(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    /** 清除记住的目录（权限由系统在应用数据清除时回收） */
    fun clearTree(context: Context) {
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }

    /**
     * 持久化目录读取权限并记住目录。
     * 先按只读申请（OpenDocumentTree 默认授予读写，取子集即可），
     * 个别提供方只接受读写组合时再降级重试。
     */
    fun persist(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        saveTree(context, uri)
    }

    /**
     * 列出目录（直接子级）内的 APK / APKS / XAPK / APKM 文件。
     * 提供方未上报 MIME 时按文件名扩展名判定。
     */
    fun listPackages(context: Context, tree: Uri): Result<List<TianjiSafEntry>> = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val out = mutableListOf<TianjiSafEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                if (!isPackageName(name)) continue
                val childId = cursor.getString(idIdx) ?: continue
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
                out += TianjiSafEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId),
                    name = name,
                    size = size,
                )
            }
        } ?: error("无法读取所选目录（授权可能已失效，可更换目录重试）")
        out.sortedBy { it.name.lowercase() }
    }

    /** 安装包扩展名判定（与 [TianjiAnalyzer] 支持的格式一致，另含 .apkm） */
    private fun isPackageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm")
    }
}

// ───────────────────────── 目录选择卡片 UI ─────────────────────────

/**
 * 「从目录选择」卡片（安装标签页内嵌）。
 *
 *  · 未记住目录 → 展示说明与「选择目录…」按钮（`ACTION_OPEN_DOCUMENT_TREE`）；
 *  · 已记住目录 → 直接列举其中的安装包，勾选后一键加入批量队列；
 *  · 支持「更换目录 / 清除记忆」。
 *
 * 加入队列只传 uri，复制到缓存与解析统一由批量页 [prepareBatchItem] 完成
 * （复用 [copyToCache]），此处不重复复制。
 */
@Composable
fun TianjiSafDirCard(accent: Color, onEnqueue: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tree by remember { mutableStateOf(TianjiSafDir.savedTree(context)) }
    var entries by remember { mutableStateOf<List<TianjiSafEntry>?>(null) }
    var message by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun reload() {
        val t = tree ?: return
        scope.launch {
            message = ""
            entries = null
            val result = withContext(Dispatchers.IO) { TianjiSafDir.listPackages(context, t) }
            entries = result.getOrElse {
                message = "列举失败：${it.message ?: "未知错误"}"
                emptyList()
            }
            selected = emptySet()
        }
    }

    LaunchedEffect(tree) { reload() }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            TianjiSafDir.persist(context, uri)
            tree = uri
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ 从目录选择", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.weight(1f))
            if (tree != null) {
                Text(
                    "更换目录",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { treeLauncher.launch(null) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "清除记忆",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF6B9D),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            TianjiSafDir.clearTree(context)
                            tree = null
                            entries = null
                            selected = emptySet()
                            message = ""
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        when {
            tree == null -> {
                Text(
                    "选择一个常用目录（如「下载」）并授权，之后可在此直接勾选其中的 APK / APKS / XAPK 批量入队；授权会持久保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWhite.copy(alpha = 0.65f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .clickable { treeLauncher.launch(null) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("选择目录…", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
                }
            }

            entries == null -> Text(
                "正在列举目录内的安装包…",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.6f),
            )

            message.isNotEmpty() && entries?.isEmpty() == true -> Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF6B9D),
            )

            entries.isNullOrEmpty() -> Text(
                "目录内未找到 APK / APKS / XAPK 文件",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.6f),
            )

            else -> {
                val list = entries.orEmpty()
                Text(
                    "共 ${list.size} 个安装包，勾选后加入批量队列",
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                )
                list.forEach { e ->
                    val key = e.uri.toString()
                    val checked = key in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selected = if (checked) selected - key else selected + key }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on -> selected = if (on) selected + key else selected - key },
                            colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = StarWhite.copy(alpha = 0.5f)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = StarWhite.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (e.size >= 0) formatBytes(e.size) else "大小未知",
                                style = MaterialTheme.typography.labelSmall,
                                color = StarWhite.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
                if (selected.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(StarGold.copy(alpha = 0.16f))
                            .clickable {
                                val uris = list.filter { it.uri.toString() in selected }.map { it.uri }
                                onEnqueue(uris)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "加入批量队列（${selected.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = StarGold,
                        )
                    }
                }
            }
        }
    }
}
