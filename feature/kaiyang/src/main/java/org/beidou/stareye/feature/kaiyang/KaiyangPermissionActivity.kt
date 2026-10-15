package org.beidou.stareye.feature.kaiyang

import android.content.pm.PackageManager
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
import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener
import com.rosan.dhizuku.shared.DhizukuVariables
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.BeidouTheme
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

/**
 * 开阳中枢授权确认页。
 *
 * 由第三方应用（经 Dhizuku-API）以
 * com.rosan.dhizuku.action.request.permission 启动，
 * 经转接层路由到本应用。协议处理与原版 Dhizuku 一致，界面为星空玻璃风格。
 */
class KaiyangPermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 解析申请（协议同原版：Bundle 中携带 uid 与请求回调 binder）
        val bundle = listOfNotNull(intent.extras, intent.getBundleExtra("bundle"))
            .firstOrNull { it.containsKey(DhizukuVariables.PARAM_CLIENT_UID) }
        if (bundle == null) {
            finish()
            return
        }

        val uid = bundle.getInt(DhizukuVariables.PARAM_CLIENT_UID, -1)
        val binder = bundle.getBinder(DhizukuVariables.PARAM_CLIENT_REQUEST_PERMISSION_BINDER)
        if (uid == -1 || binder == null) {
            finish()
            return
        }

        val listener = runCatching {
            IDhizukuRequestPermissionListener.Stub.asInterface(binder)
        }.getOrNull()
        if (listener == null) {
            finish()
            return
        }

        val pm = packageManager
        val packageInfo = pm.getPackageInfoForUid(uid)
        val label = try {
            packageInfo?.applicationInfo?.loadLabel(pm)?.toString() ?: "未知应用"
        } catch (_: Throwable) {
            "未知应用"
        }
        val icon: Bitmap? = try {
            packageInfo?.applicationInfo?.loadIcon(pm)?.toBitmap(144, 144)
        } catch (_: Throwable) {
            null
        }
        val signature = packageInfo?.signature ?: ""

        setContent {
            BeidouTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    KaiyangPermissionDialog(
                        label = label,
                        icon = icon,
                        onAllow = {
                            record(uid, signature, allow = true)
                            reply(listener, PackageManager.PERMISSION_GRANTED)
                            finish()
                        },
                        onDeny = {
                            record(uid, signature, allow = false)
                            reply(listener, PackageManager.PERMISSION_DENIED)
                            finish()
                        },
                    )
                }
            }
        }
    }

    /** 记录授权结果（对应原版 AppRepo 的 insert/update） */
    private fun record(uid: Int, signature: String, allow: Boolean) {
        val existing = KaiyangStore.findByUid(this, uid)
        KaiyangStore.upsert(
            this,
            (existing ?: KaiyangApp(uid = uid)).copy(
                uid = uid,
                signature = signature,
                allowApi = allow,
            ),
        )
    }

    private fun reply(listener: IDhizukuRequestPermissionListener, result: Int) {
        runCatching { listener.onRequestPermission(result) }
    }
}

@Composable
private fun KaiyangPermissionDialog(
    label: String,
    icon: Bitmap?,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val accent = starAccent(6)
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
            text = "开阳中枢授权申请",
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
            text = "请求获得 Dhizuku（开阳中枢）授权，以使用设备所有者能力。",
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
                Text("拒绝", color = StarWhite.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold)
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
                Text("允许授权", color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
