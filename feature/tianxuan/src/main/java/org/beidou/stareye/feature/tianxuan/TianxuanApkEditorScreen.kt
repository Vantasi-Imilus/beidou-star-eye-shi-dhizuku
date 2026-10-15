package org.beidou.stareye.feature.tianxuan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.zip.ZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 天璇中枢 —— APK 文件级编辑页。
 *
 * 将应用主 APK 读入本地缓存后以 zip 条目视角列出内容：
 * 删除条目 / 用设备上的文件替换同名条目 / 添加新文件；
 * 保存时重建 zip（.so 原样存储，其余压缩），并尝试用自生成密钥重签名（v1+v2+v3）。
 */
@Composable
fun TianxuanApkEditorScreen(app: TianxuanApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianxuanFeature.star.order)

    var apkFile by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<TianxuanApkEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var deleted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var replaced by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var added by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var pickReplaceTarget by remember { mutableStateOf<String?>(null) }
    var pickAdd by remember { mutableStateOf(false) }
    var addSource by remember { mutableStateOf<File?>(null) }
    var addName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }

    // 文件选择器（替换 / 新增共用，按当前 pending 状态分流）
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            pickReplaceTarget = null
            pickAdd = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) { copyPickedFile(context, uri) }
            val target = pickReplaceTarget
            when {
                file == null -> {
                    message = "读取所选文件失败"
                    pickReplaceTarget = null
                    pickAdd = false
                }

                target != null -> {
                    pickReplaceTarget = null
                    replaced = replaced + (target to file)
                    message = "已替换条目：$target"
                }

                pickAdd -> {
                    pickAdd = false
                    addSource = file
                    addName = file.name
                }
            }
        }
    }

    LaunchedEffect(app.packageName) {
        loading = true
        val r = withContext(Dispatchers.IO) { TianxuanApkEditor.prepareLocalApk(context, app) }
        if (r.isSuccess) {
            apkFile = r.getOrNull()
            entries = withContext(Dispatchers.IO) { TianxuanApkEditor.listEntries(apkFile!!) }
        } else {
            loadError = r.exceptionOrNull()?.message ?: "读取 APK 失败"
        }
        loading = false
    }

    fun save() {
        val src = apkFile ?: return
        scope.launch {
            busy = "保存"
            message = ""
            val r = withContext(Dispatchers.IO) {
                TianxuanApkEditor.saveEditedApk(context, app, src, deleted, replaced, added.toList())
            }
            busy = null
            val ok = r.isSuccess
            val outcome = r.getOrNull()
            val detail = if (ok && outcome != null) {
                if (outcome.signed) "已保存并重签名：${outcome.path}"
                else "已保存（未重签名）：${outcome.path}；重签名失败：${outcome.signError}"
            } else {
                "失败：${r.exceptionOrNull()?.message ?: "未知错误"}"
            }
            message = detail
            TianxuanLog.add(
                context,
                TianxuanLogEntry(System.currentTimeMillis(), "APK编辑保存", app.packageName, app.label, ok, detail),
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glass(shape = RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StarWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("APK 编辑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StarWhite)
                Text(
                    "${app.label} · ${app.packageName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(enabled = busy == null && apkFile != null) { save() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    if (busy == "保存") "保存中…" else "保存为新 APK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (busy != null) StarWhite.copy(alpha = 0.4f) else accent,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在读取 APK…", color = StarWhite.copy(alpha = 0.6f))
            }
        } else if (loadError.isNotEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(16.dp)).padding(14.dp),
                ) {
                    Text("✦ $loadError（需要天枢中枢就绪）", style = MaterialTheme.typography.bodySmall, color = StarGold)
                }
            }
        } else {
            // 操作卡
            Column(
                modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(StarBlue.copy(alpha = 0.16f))
                            .clickable(enabled = busy == null) { pickAdd = true; picker.launch(arrayOf("*/*")) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("＋ 添加文件", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = StarBlue)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                            .clickable(enabled = busy == null && (deleted.isNotEmpty() || replaced.isNotEmpty() || added.isNotEmpty())) {
                                deleted = emptySet()
                                replaced = emptyMap()
                                added = emptyMap()
                                message = "已撤销全部编辑"
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("撤销全部编辑", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF6B9D))
                    }
                }
                Text(
                    "保存时重建 zip（.so 原样存储，其余压缩），并用自生成 RSA 密钥重签名（v1+v2+v3）；重签名失败会退化为未签名产物。",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "zip 条目 ${entries.size} 项（已删除 ${deleted.size} · 已替换 ${replaced.size} · 新增 ${added.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.name }) { entry ->
                    EntryRow(
                        entry = entry,
                        deleted = entry.name in deleted,
                        replacementName = replaced[entry.name]?.name,
                        busy = busy != null,
                        accent = accent,
                        onDelete = {
                            deleted = deleted + entry.name
                            replaced = replaced - entry.name
                        },
                        onUndoDelete = { deleted = deleted - entry.name },
                        onReplace = {
                            pickReplaceTarget = entry.name
                            picker.launch(arrayOf("*/*"))
                        },
                        onUndoReplace = { replaced = replaced - entry.name },
                    )
                }
                // 新增条目区
                added.forEach { (name, file) ->
                    item(key = "added-$name") {
                        AddedRow(
                            name = name,
                            sourceName = file.name,
                            busy = busy != null,
                            onRemove = { added = added - name },
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    // 新增条目命名对话框
    if (addSource != null) {
        AlertDialog(
            onDismissRequest = { addSource = null },
            title = { Text("添加条目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("条目名称（APK 包内路径）：", style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.7f))
                    TextField(
                        value = addName,
                        onValueChange = { addName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = accent,
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = addName.trim()
                    val exists = entries.any { it.name == name && it.name !in deleted && it.name !in replaced }
                    when {
                        !isValidEntryName(name) -> message = "条目名不合法（不能为空、以 / 开头、含 .. 或以 / 结尾）"
                        exists || added.containsKey(name) -> message = "同名条目已存在，请改名或先删除原条目"
                        else -> {
                            added = added + (name to addSource!!)
                            message = "已添加：$name"
                            addSource = null
                        }
                    }
                }) { Text("添加", color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { addSource = null }) { Text("取消") }
            },
        )
    }
}

/** 条目名合法性校验（防 zip 路径逃逸） */
private fun isValidEntryName(name: String): Boolean =
    name.isNotBlank() && !name.startsWith("/") && !name.contains("..") && !name.endsWith("/") && !name.contains('\\')

/** 把 SAF 选中的文档复制到应用缓存，返回本地文件 */
private fun copyPickedFile(context: android.content.Context, uri: Uri): File? = try {
    val dir = File(context.cacheDir, "apkedit-in")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "in-${System.currentTimeMillis()}-${uri.hashCode()}")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { input.copyTo(it) }
    } ?: return null
    file
} catch (_: Throwable) {
    null
}

// ───────────────────────── 条目行 ─────────────────────────

@Composable
private fun EntryRow(
    entry: TianxuanApkEntry,
    deleted: Boolean,
    replacementName: String?,
    busy: Boolean,
    accent: Color,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    onReplace: () -> Unit,
    onUndoReplace: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (deleted) StarWhite.copy(alpha = 0.35f) else StarWhite.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (deleted) {
                EntryBadge("已删除", Color(0xFFFF6B9D))
            } else if (replacementName != null) {
                EntryBadge("已替换", StarBlue)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryBadge(
                when {
                    entry.isDirectory -> "目录"
                    entry.method == ZipEntry.STORED -> "STORED"
                    else -> "DEFLATED"
                },
                StarGold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (entry.isDirectory) "—" else formatBytes(entry.size),
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.45f),
            )
            if (replacementName != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "新文件：$replacementName",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarBlue.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            if (deleted) {
                RowChip("撤销", accent, busy) { onUndoDelete() }
            } else if (entry.isDirectory) {
                RowChip("删除", Color(0xFFFF6B9D), busy) { onDelete() }
            } else {
                RowChip(if (replacementName != null) "重新替换" else "替换", StarBlue, busy) { onReplace() }
                Spacer(Modifier.width(8.dp))
                RowChip(if (replacementName != null) "还原" else "删除", Color(0xFFFF6B9D), busy) {
                    if (replacementName != null) onUndoReplace() else onDelete()
                }
            }
        }
    }
}

@Composable
private fun AddedRow(name: String, sourceName: String, busy: Boolean, onRemove: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = StarBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EntryBadge("新增", StarBlue)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "来源文件：$sourceName",
                style = MaterialTheme.typography.labelSmall,
                color = StarWhite.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RowChip("移除", Color(0xFFFF6B9D), busy) { onRemove() }
        }
    }
}

@Composable
private fun EntryBadge(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun RowChip(label: String, color: Color, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (busy) StarWhite.copy(alpha = 0.35f) else color,
        )
    }
}
