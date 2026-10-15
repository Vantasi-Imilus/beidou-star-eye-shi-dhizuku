package org.beidou.stareye.feature.tianquan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

/**
 * 天权中枢 —— 局域网文件服务面板（HTTP / FTP）。
 *
 * 开启后可在电脑浏览器或文件管理器中直接访问手机上的共享目录，
 * 与 Termux-X 的「ZT 下载服务器 / FTP 服务」定位一致。
 */
@Composable
fun TianquanFileServiceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccentSafe()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    // 用计数器驱动重读运行状态
    var tick by remember { mutableIntStateOf(0) }

    val httpRunning = remember(tick) { TianquanFileServers.httpRunning }
    val ftpRunning = remember(tick) { TianquanFileServers.ftpRunning }
    val httpUrl = remember(tick) { TianquanFileServers.http?.url().orEmpty() }
    val ftpUrl = remember(tick) { TianquanFileServers.ftp?.url().orEmpty() }
    val root = remember(tick) { TianquanFileServers.shareRoot(context) }

    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("beidou", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("局域网文件服务") },
        text = {
            Column(
                modifier = Modifier.height(400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "在电脑浏览器或文件管理器中访问下列地址，即可与手机互传文件。共享目录为终端的工作目录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWhite.copy(alpha = 0.7f),
                )

                // ── 共享目录 ──
                ServiceCard("共享目录", StarWhite.copy(alpha = 0.85f)) {
                    Text(
                        root.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = StarWhite.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip("默认目录", accent) {
                            TianquanFileServers.setShareRoot(context, TianquanFileServers.defaultRoot(context))
                            tick++
                        }
                    }
                }

                // ── HTTP ──
                ServiceCard("HTTP 文件服务", accent) {
                    StatusLine(httpRunning)
                    if (httpRunning && httpUrl.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            httpUrl,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniChip("复制地址", accent) { copy(httpUrl) }
                            MiniChip("停止", Color(0xFFFF6B9D)) {
                                busy = true
                                scope.launch {
                                    withContext(Dispatchers.IO) { TianquanFileServers.stopHttp() }
                                    busy = false
                                    message = "HTTP 服务已停止"
                                    tick++
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        MiniChip("启动 HTTP", accent) {
                            busy = true
                            message = ""
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { TianquanFileServers.startHttp(context) }
                                busy = false
                                message = r.fold(
                                    onSuccess = { "HTTP 已启动：$it" },
                                    onFailure = { "启动失败：${it.message ?: "未知错误"}" },
                                )
                                tick++
                            }
                        }
                    }
                }

                // ── FTP ──
                ServiceCard("FTP 文件服务", StarGold) {
                    StatusLine(ftpRunning, runningColor = StarGold)
                    if (ftpRunning && ftpUrl.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ftpUrl,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = StarGold,
                        )
                        Text(
                            "账号 ${TianquanFtpServer.DEFAULT_USER} / 密码 ${TianquanFtpServer.DEFAULT_PASSWORD}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StarWhite.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniChip("复制地址", StarGold) { copy(ftpUrl) }
                            MiniChip("停止", Color(0xFFFF6B9D)) {
                                busy = true
                                scope.launch {
                                    withContext(Dispatchers.IO) { TianquanFileServers.stopFtp() }
                                    busy = false
                                    message = "FTP 服务已停止"
                                    tick++
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        MiniChip("启动 FTP", StarGold) {
                            busy = true
                            message = ""
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { TianquanFileServers.startFtp(context) }
                                busy = false
                                message = r.fold(
                                    onSuccess = { "FTP 已启动：$it" },
                                    onFailure = { "启动失败：${it.message ?: "未知错误"}" },
                                )
                                tick++
                            }
                        }
                    }
                }

                if (busy) {
                    Text("处理中…", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.6f))
                }
                if (message.isNotEmpty()) {
                    Text(message, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.85f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { TianquanFileServers.stopAll(context) }
                    message = "全部服务已停止"
                    tick++
                }
            }) { Text("停止全部", color = Color(0xFFFF6B9D)) }
        },
    )
}

@Composable
private fun ServiceCard(title: String, titleColor: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(beidouShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun StatusLine(running: Boolean, runningColor: Color = Color(0xFF7CE0C3)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(beidouShape(50))
                .background(if (running) runningColor else Color.White.copy(alpha = 0.25f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (running) "运行中" else "未运行",
            style = MaterialTheme.typography.labelMedium,
            color = if (running) runningColor else StarWhite.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MiniChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(beidouShape(50))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun starAccentSafe() =
    starAccent(TianquanFeature.star.order)
