package org.beidou.stareye.feature.tianshu

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Parcel
import androidx.core.graphics.drawable.toBitmap
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

/** 已授权应用条目 */
data class TianshuApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val icon: Bitmap?,
)

/** 授权等级 */
enum class AuthLevel { NONE, ADB, ROOT, UNKNOWN }

/**
 * 天枢中枢管理逻辑：状态、授权等级、应用授权列表、停止服务。
 * 全部基于原版 Shizuku API，不修改其授权逻辑。
 */
object TianshuManager {

    private const val FLAG_ALLOWED = 1 shl 1
    private const val MASK_PERMISSION = FLAG_ALLOWED or (1 shl 2)

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun version(): Int = try {
        if (isRunning()) Shizuku.getVersion() else -1
    } catch (_: Throwable) {
        -1
    }

    fun isSelfGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** 依据 server uid 判定授权等级：0=root，2000=shell(adb) */
    fun authLevel(): AuthLevel = try {
        if (!isRunning()) {
            AuthLevel.NONE
        } else {
            when (Shizuku.getUid()) {
                0 -> AuthLevel.ROOT
                2000 -> AuthLevel.ADB
                else -> AuthLevel.UNKNOWN
            }
        }
    } catch (_: Throwable) {
        AuthLevel.NONE
    }

    /** 停止天枢（Shizuku server） */
    fun stopServer() {
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
        }
    }

    fun listApps(context: Context): List<TianshuApp> {
        if (!isRunning()) return emptyList()
        val pm = context.packageManager
        val packages = try {
            getApplications(-1)
        } catch (_: Throwable) {
            emptyList()
        }
        return packages.mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (_: Throwable) {
                pi.packageName
            }
            val icon = try {
                ai.loadIcon(pm).toBitmap(96, 96)
            } catch (_: Throwable) {
                null
            }
            TianshuApp(pi.packageName, label, ai.uid, icon)
        }.sortedBy { it.label }
    }

    fun isAppGranted(uid: Int): Boolean = try {
        (Shizuku.getFlagsForUid(uid, MASK_PERMISSION) and FLAG_ALLOWED) == FLAG_ALLOWED
    } catch (_: Throwable) {
        false
    }

    fun setAppGranted(uid: Int, granted: Boolean) {
        try {
            Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, if (granted) FLAG_ALLOWED else 0)
        } catch (_: Throwable) {
        }
    }

    /** 通过 server 的自定义事务获取已申请 Shizuku 权限的应用列表 */
    private fun getApplications(userId: Int): List<PackageInfo> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeInt(userId)
            Shizuku.getBinder()!!.transact(ServerConstants.BINDER_TRANSACTION_getApplications, data, reply, 0)
            reply.readException()
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list!!
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
