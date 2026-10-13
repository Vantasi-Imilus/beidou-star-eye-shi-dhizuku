package org.beidou.stareye.feature.tianshu

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.launch

/**
 * 天枢中枢激活引导卡：
 * 展示激活脚本路径与全文、可复制的一键 ADB 指令、
 * 自动检测无线调试端口并生成对应的一键激活指令，以及 Root 方式。
 */
@Composable
fun ActivationCard() {
    val context = LocalContext.current
    val pkg = context.packageName
    val starterPath = "${context.applicationInfo.nativeLibraryDir}/libshizuku.so"
    val scriptPath = "/sdcard/Android/data/$pkg/files/start.sh"
    val scriptContent = "#!/system/bin/sh\n" +
        "# 北斗·群星之眼 · 天枢中枢 激活脚本\n" +
        "exec \"$starterPath\"\n"

    var wirelessPort by remember { mutableStateOf(-1) }
    var deviceIp by remember { mutableStateOf<String?>(null) }
    var activating by remember { mutableStateOf(false) }
    var wirelessOutput by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 写入激活脚本 + 检测无线调试端口与设备 IP
    LaunchedEffect(Unit) {
        try {
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                val f = File(dir, "start.sh")
                f.writeText(scriptContent)
                f.setExecutable(true, false)
            }
        } catch (_: Throwable) {
        }
        wirelessPort = detectWirelessPort(context)
        deviceIp = detectDeviceIp(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "✦ 激活天枢中枢",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StarBlue,
        )
        Text(
            text = "本中枢内置完整的 Shizuku 服务。激活后，第三方应用的 Shizuku 授权申请将自动转接到本应用处理。",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.8f),
        )

        // ── 方式一：ADB ──
        CommandBlock(
            context = context,
            title = "方式一 · ADB 一键激活（复制到电脑终端执行）",
            command = "adb shell sh $scriptPath",
        )
        Text(
            text = "激活脚本位置：$scriptPath",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.5f),
        )
        CopyChip(context = context, label = "复制脚本全文", text = scriptContent)

        // ── 方式二：无线调试（自动检测端口）──
        val detected = wirelessPort in 1..65535
        val target = when {
            detected && deviceIp != null -> "$deviceIp:$wirelessPort"
            detected -> "<手机IP>:$wirelessPort"
            else -> "<手机IP>:<端口>"
        }
        Text(
            text = "方式二 · 无线调试（adb connect）",
            style = MaterialTheme.typography.labelMedium,
            color = StarGold.copy(alpha = 0.9f),
        )
        Text(
            text = if (detected) {
                "✓ 已检测到无线调试端口：$target"
            } else {
                "未检测到无线调试端口，请先在「开发者选项」中开启无线调试"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (detected) StarBlue else StarWhite.copy(alpha = 0.5f),
        )
        CommandBlock(
            context = context,
            title = "依次执行：",
            command = "adb connect $target\n" +
                "adb -s $target shell sh $scriptPath",
        )

        // 一键无线激活：直接在本应用内通过检测到的端口完成激活
        if (detected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(16.dp))
                    .clickable(enabled = !activating) {
                        activating = true
                        wirelessOutput = ""
                        scope.launch {
                            val port = wirelessPort
                            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                // 直接连本机 adbd（127.0.0.1），无需网络
                                WirelessActivator.activate(context, "127.0.0.1", port, scriptPath) { out ->
                                    wirelessOutput += out
                                }
                            }
                            if (ok) {
                                // 激活成功：重启应用以接收 server 分发的 binder
                                wirelessOutput += "\n✅ 激活成功，正在重启应用以接管天枢…"
                                kotlinx.coroutines.delay(1800)
                                restartApp(context)
                            } else {
                                kotlinx.coroutines.delay(1200)
                                activating = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (activating) "激活中…（如弹出「允许调试」请点允许）" else "⚡ 一键无线激活",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StarBlue,
                )
            }
        }
        if (wirelessOutput.isNotEmpty()) {
            Text(
                text = wirelessOutput.trim(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.7f),
            )
        }

        // ── 方式三：Root ──
        CommandBlock(
            context = context,
            title = "方式三 · Root",
            command = "su -c \"sh $scriptPath\"",
        )

        Text(
            text = "激活成功后本页会自动刷新为「运行中」。",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.4f),
        )
    }
}

/** 重启应用进程（用于激活后接管 server 分发的 binder） */
private fun restartApp(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        context.startActivity(intent)
    } catch (_: Throwable) {
    }
    Runtime.getRuntime().exit(0)
}

/** 读取系统属性（service.adb.tcp.port 等） */
private fun readSystemProperty(name: String): String = try {
    val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", name))
    val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
    process.destroy()
    value
} catch (_: Throwable) {
    ""
}

/** 自动检测无线调试端口 */
private fun detectWirelessPort(context: Context): Int {
    // 1. service.adb.tcp.port / persist.adb.tcp.port
    for (key in listOf("service.adb.tcp.port", "persist.adb.tcp.port")) {
        readSystemProperty(key).toIntOrNull()?.let { if (it in 1..65535) return it }
    }
    // 2. Settings.Global adb_wifi_port
    try {
        val port = android.provider.Settings.Global.getInt(
            context.contentResolver, "adb_wifi_port", -1,
        )
        if (port in 1..65535) return port
    } catch (_: Throwable) {
    }
    return -1
}

/** 自动检测本机局域网 IPv4 地址 */
private fun detectDeviceIp(context: Context): String? {
    // 1. ConnectivityManager（最可靠）
    try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) }
        lp?.linkAddresses?.forEach { la ->
            val addr = la.address
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                return addr.hostAddress
            }
        }
    } catch (_: Throwable) {
    }
    // 2. NetworkInterface 回退
    return try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun CommandBlock(context: Context, title: String, command: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = StarGold.copy(alpha = 0.9f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = StarWhite.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(4.dp))
            CopyChip(context = context, label = "复制", text = command)
        }
    }
}

@Composable
private fun CopyChip(context: Context, label: String, text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StarBlue.copy(alpha = 0.18f))
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("beidou", text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = StarBlue,
        )
    }
}
