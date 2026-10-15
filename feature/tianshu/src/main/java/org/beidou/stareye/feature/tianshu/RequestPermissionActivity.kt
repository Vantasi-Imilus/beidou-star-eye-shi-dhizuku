package org.beidou.stareye.feature.tianshu

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.BeidouTheme
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

/**
 * 天枢中枢 —— 授权确认页。
 *
 * 由已激活的本应用 Shizuku server（shell 身份）以 action
 * {applicationId}.intent.action.REQUEST_PERMISSION 启动，
 * 展示申请方信息并回传授权结果。
 * 申请/授权协议与逻辑完全沿用原版 Shizuku，仅界面为星空玻璃风格。
 */
class RequestPermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai = intent.getParcelableExtra<ApplicationInfo>("applicationInfo")

        if (uid == -1 || pid == -1 || ai == null) {
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            ai.packageName
        }
        val icon: Bitmap? = try {
            ai.loadIcon(packageManager).toBitmap(144, 144)
        } catch (e: Exception) {
            null
        }

        setContent {
            BeidouTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PermissionDialog(
                        label = label,
                        icon = icon,
                        onAllow = {
                            reply(uid, pid, requestCode, allowed = true, onetime = false)
                            finish()
                        },
                        onDeny = {
                            reply(uid, pid, requestCode, allowed = false, onetime = true)
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun reply(uid: Int, pid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val data = Bundle()
        data.putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        try {
            Shizuku.dispatchPermissionConfirmationResult(uid, pid, requestCode, data)
        } catch (_: Throwable) {
        }
    }
}

@Composable
private fun PermissionDialog(
    label: String,
    icon: Bitmap?,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val accent = starAccent(1)
    Column(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .glass(shape = beidouShape(28.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        } else {
            StarGlyph(color = accent, size = 52.dp)
        }

        Text(
            text = "授权申请",
            style = MaterialTheme.typography.labelMedium,
            color = StarGold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = StarWhite,
        )
        Text(
            text = "请求获得 Shizuku（天枢中枢）授权，以调用系统级 API。",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.7f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(beidouShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onDeny)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "拒绝",
                    color = StarWhite.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(beidouShape(16.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .clickable(onClick = onAllow)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "允许授权",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
