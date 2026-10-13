package org.beidou.stareye.feature.kaiyang

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.server_api.DhizukuProvider
import com.rosan.dhizuku.server_api.DhizukuService

/**
 * 开阳中枢 Device Admin 接收器 —— Device Owner 的载体组件。
 */
class KaiyangDAReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val type = when {
            KaiyangState.isDeviceOwner(context) -> "设备所有者"
            KaiyangState.isProfileOwner(context) -> "资料所有者"
            else -> null
        }
        if (type != null) {
            Toast.makeText(context, "开阳中枢已激活（$type）", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 开阳中枢服务：权限校验逻辑与原版 Dhizuku 一致（不改授权逻辑）。
 */
class KaiyangService(
    context: Context,
    admin: ComponentName?,
    client: IDhizukuClient?,
) : DhizukuService(context, admin, client) {

    override fun getVersionName(): String = "1.0"

    override fun checkCallingPermission(func: String?, callingUid: Int, callingPid: Int): Boolean {
        if (!KaiyangStore.isEnabled(mContext)) {
            return false
        }

        val entity = KaiyangStore.findByUid(mContext, callingUid) ?: return false
        if (!entity.allowApi || entity.blocked) {
            return false
        }

        if (KaiyangStore.isWhitelistMode(mContext) && !entity.allowApi) {
            return false
        }

        val signature = mContext.packageManager.getPackageInfoForUid(callingUid)?.signature
            ?: return false
        if (signature != entity.signature) {
            return false
        }

        return true
    }
}

/**
 * 开阳中枢 Provider —— 转接层载体：
 * 在本应用内以 com.rosan.dhizuku.server.provider 等 authority 暴露，
 * 使第三方应用的硬编码申请直接路由到开阳中枢。
 */
class KaiyangProvider : DhizukuProvider() {

    override fun onCreateService(client: IDhizukuClient): DhizukuService {
        val ctx = context ?: error("context is null")
        return KaiyangService(ctx, KaiyangState.admin(ctx), client)
    }
}
