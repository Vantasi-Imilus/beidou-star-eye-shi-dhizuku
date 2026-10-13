package org.beidou.stareye.feature.tianji

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite

/**
 * 天玑中枢 —— 设置标签页：安装配置（授权器 / 安装原因 / 请求方 / 各开关）与禁止安装名单。
 */
@Composable
fun TianjiSettingsTab(accent: Color) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(TianjiPrefs.options(context)) }
    var blacklist by remember { mutableStateOf(TianjiPrefs.blacklist(context)) }
    var newBlocked by remember { mutableStateOf("") }

    fun update(o: TianjiOptions) {
        options = o
        TianjiPrefs.saveOptions(context, o)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ── 授权器 ──
        SectionCard("✦ 安装授权器", accent) {
            Text(
                "选择执行静默安装的特权通道；「自动」会按 天枢 → 开阳 → Root 顺序选用首个可用者。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))
            Authorizer.entries.forEach { a ->
                val ready = TianjiPrivileged.ready(context, a)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (options.authorizer == a) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { update(options.copy(authorizer = a)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (options.authorizer == a) "◉" else "○",
                        color = if (options.authorizer == a) accent else StarWhite.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = StarWhite)
                        Text(a.detail, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
                    }
                    Text(
                        if (ready) "可用" else "不可用",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ready) StarBlue else StarWhite.copy(alpha = 0.35f),
                    )
                }
            }
        }

        // ── 安装原因 ──
        SectionCard("✦ 安装原因", accent) {
            Text(
                "对应 `pm install --install-reason`，供系统与设备策略识别本次安装的来由。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))
            OptionChips(
                values = InstallReason.entries.toList(),
                selected = options.installReason,
                label = { "${it.label} (${it.code})" },
                accent = accent,
                onSelect = { update(options.copy(installReason = it)) },
            )
        }

        // ── 安装请求方 ──
        SectionCard("✦ 安装请求方", accent) {
            Text(
                "写入应用记录的「安装来源包名」（`pm install -i`）。留空则不指定。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = options.installerPackage,
                onValueChange = { update(options.copy(installerPackage = it)) },
                modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(14.dp)),
                placeholder = { Text("如：com.android.vending", color = StarWhite.copy(alpha = 0.3f)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = accent,
                    focusedTextColor = StarWhite,
                    unfocusedTextColor = StarWhite,
                ),
            )
        }

        // ── 安装策略开关 ──
        SectionCard("✦ 安装策略", accent) {
            ToggleRow("为所有用户安装", "使用 `--user all`，对设备上全部用户生效", options.forAllUsers, accent) {
                update(options.copy(forAllUsers = it))
            }
            ToggleRow("允许测试包安装", "对应 `-t`，允许安装 android:testOnly 标记的包", options.allowTestPackages, accent) {
                update(options.copy(allowTestPackages = it))
            }
            ToggleRow("允许降级安装", "对应 `-d`，允许版本号低于已安装版本", options.allowDowngrade, accent) {
                update(options.copy(allowDowngrade = it))
            }
            ToggleRow("默认选择所有分包", "归档（APKS/XAPK）安装时默认勾选全部分包", options.selectAllSplits, accent) {
                update(options.copy(selectAllSplits = it))
            }
            ToggleRow(
                "安装需生物认证",
                if (TianjiBiometric.available(context)) {
                    "安装前需通过指纹 / 人脸验证"
                } else {
                    "当前设备不支持生物认证，将跳过校验"
                },
                options.requireBiometrics,
                accent,
            ) {
                update(options.copy(requireBiometrics = it))
            }
        }

        // ── 禁止安装名单 ──
        SectionCard("✦ 禁止安装的应用", StarGold) {
            Text(
                "命中名单的包名将被拒绝安装（在天玑中枢内的所有安装通道生效）。",
                style = MaterialTheme.typography.bodySmall,
                color = StarWhite.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newBlocked,
                    onValueChange = { newBlocked = it },
                    modifier = Modifier.weight(1f).glass(shape = RoundedCornerShape(14.dp)),
                    placeholder = { Text("输入要禁止的包名", color = StarWhite.copy(alpha = 0.3f)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = accent,
                        focusedTextColor = StarWhite,
                        unfocusedTextColor = StarWhite,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "添加",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.18f))
                        .clickable {
                            TianjiPrefs.addBlacklist(context, newBlocked)
                            blacklist = TianjiPrefs.blacklist(context)
                            newBlocked = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            if (blacklist.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("名单为空", style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.45f))
            } else {
                Spacer(Modifier.height(10.dp))
                blacklist.sorted().forEach { pkg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pkg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = StarWhite.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "移除",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF6B9D),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFF6B9D).copy(alpha = 0.14f))
                                .clickable {
                                    TianjiPrefs.removeBlacklist(context, pkg)
                                    blacklist = TianjiPrefs.blacklist(context)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(shape = RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(10.dp))
        content()
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun <T> OptionChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    accent: Color,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { v ->
                    val on = v == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                            .clickable { onSelect(v) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(v),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) accent else StarWhite.copy(alpha = 0.6f),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = StarWhite)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = StarWhite.copy(alpha = 0.5f))
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.35f),
                uncheckedThumbColor = StarWhite.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}
