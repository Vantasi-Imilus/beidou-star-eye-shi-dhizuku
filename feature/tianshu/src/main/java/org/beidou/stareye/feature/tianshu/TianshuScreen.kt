package org.beidou.stareye.feature.tianshu

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import rikka.shizuku.Shizuku

/**
 * 天枢中枢 —— Shizuku 服务管理界面。
 *
 * 展示服务状态与授权等级、管理各应用的授权开关、随时关闭天枢。
 * 申请/授权逻辑完全沿用原版 Shizuku，仅界面为星空玻璃风格。
 */
@Composable
fun TianshuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = starAccent(TianshuFeature.star.order)

    var serverRunning by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(-1) }
    var granted by remember { mutableStateOf(false) }
    var authLevel by remember { mutableStateOf(AuthLevel.NONE) }
    var apps by remember { mutableStateOf<List<TianshuApp>>(emptyList()) }
    var grantedMap by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }

    fun loadApps() {
        apps = TianshuManager.listApps(context)
        grantedMap = apps.associate { it.uid to TianshuManager.isAppGranted(it.uid) }
    }

    fun refresh() {
        serverRunning = TianshuManager.isRunning()
        version = TianshuManager.version()
        granted = TianshuManager.isSelfGranted()
        authLevel = TianshuManager.authLevel()
        if (serverRunning && granted) loadApps() else {
            apps = emptyList()
            grantedMap = emptyMap()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(Unit) {
        val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
        val binderDead = Shizuku.OnBinderDeadListener { refresh() }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.glass(shape = RoundedCornerShape(50)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = StarWhite,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = TianshuFeature.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StarWhite,
                )
                Text(
                    text = TianshuFeature.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = StarWhite.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 星位英雄区
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StarGlyph(color = accent, size = 44.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = TianshuFeature.star.chineseName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }

        Spacer(Modifier.height(20.dp))

        // 状态卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusRow("服务状态", if (serverRunning) "运行中" else "未运行", if (serverRunning) accent else Color.Gray)
            StatusRow("服务版本", if (version >= 0) version.toString() else "—", StarWhite.copy(alpha = 0.75f))
            AuthLevelRow(level = authLevel, granted = granted)
        }

        Spacer(Modifier.height(16.dp))

        when {
            !serverRunning -> ActivationCard()
            !granted -> RequestPermissionCard(accent = accent, onRequest = {
                Shizuku.requestPermission(Random.nextInt())
            })
            else -> {
                AuthorizedAppsCard(
                    apps = apps,
                    granted = grantedMap,
                    onToggle = { app, checked ->
                        TianshuManager.setAppGranted(app.uid, checked)
                        grantedMap = grantedMap + (app.uid to checked)
                    },
                )
                Spacer(Modifier.height(16.dp))
                StopServerButton(onStop = {
                    TianshuManager.stopServer()
                    scope.launch {
                        delay(800)
                        refresh()
                    }
                })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.55f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun AuthLevelRow(level: AuthLevel, granted: Boolean) {
    val text = if (granted) authLevelText(level) else "未授权"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "授权状态",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.55f),
        )
        Spacer(Modifier.weight(1f))
        if (granted && level == AuthLevel.ROOT) {
            RainbowText(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) authLevelColor(level) else StarGold,
            )
        }
    }
}

@Composable
private fun RequestPermissionCard(accent: Color, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "✦ 申请授权",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StarGold,
        )
        Text(
            text = "「北斗·群星之眼」需要获得 Shizuku 授权，才能调用系统级 API。",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.8f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(16.dp))
                .clickable(onClick = onRequest)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "申请授权",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun StopServerButton(onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onStop)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⏻ 关闭天枢",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StarGold,
        )
    }
}
