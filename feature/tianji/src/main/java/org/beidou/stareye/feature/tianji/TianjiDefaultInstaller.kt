package org.beidou.stareye.feature.tianji

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri

/**
 * 天玑中枢 —— 默认安装器 / 卸载器身份管理（自研实现）。
 *
 * 系统对「默认安装器」的判定 = 打开 APK 的 `ACTION_VIEW` 意图解析结果是否为本应用；
 * 对「默认卸载器」的判定 = `ACTION_DELETE` + `package:` URI 的解析结果。
 * 本应用借助**开阳中枢（Device Owner）**的公开 API
 * [DevicePolicyManager.addPersistentPreferredActivity] 持久化注册这两个身份，
 * 无需像 InstallerX 那样依赖 Shizuku 特权服务与隐藏 API。
 */
object TianjiDefaultInstaller {

    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 安装请求探针意图（与系统解析安装请求时一致） */
    private fun installProbe(): Intent = Intent(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(Uri.parse("content://storage/emulated/0/probe.apk"), MIME_APK)
    }

    /** 卸载请求探针意图 */
    private fun uninstallProbe(): Intent = Intent(Intent.ACTION_DELETE).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        data = Uri.parse("package:com.beidou.probe")
    }

    private fun isResolvedToSelf(context: Context, intent: Intent): Boolean = try {
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    } catch (_: Throwable) {
        false
    }

    /** 当前是否为系统默认安装器 */
    fun isDefault(context: Context): Boolean = isResolvedToSelf(context, installProbe())

    /** 当前是否为系统默认卸载器 */
    fun isUninstallerDefault(context: Context): Boolean =
        isResolvedToSelf(context, uninstallProbe())

    /** 是否存在可竞争安装器身份的其他应用（排除本应用） */
    fun hasRival(context: Context): Boolean = try {
        context.packageManager.queryIntentActivities(installProbe(), PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName != context.packageName }
    } catch (_: Throwable) {
        false
    }

    /** 开阳中枢（Device Owner）是否已激活 —— 自注册通道的前提 */
    fun ownerReady(context: Context): Boolean = try {
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isDeviceOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    private fun ownerAdmin(context: Context): ComponentName? = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.activeAdmins?.firstOrNull { it.packageName == context.packageName }
    } catch (_: Throwable) {
        null
    }

    /** 匹配安装请求的意图过滤器（打开 / 安装 APK） */
    private fun installerFilter(): IntentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_VIEW)
        @Suppress("DEPRECATION")
        addAction(Intent.ACTION_INSTALL_PACKAGE)
        addCategory(Intent.CATEGORY_DEFAULT)
        addDataScheme("content")
        addDataScheme("file")
        addDataType(MIME_APK)
    }

    /**
     * 匹配卸载请求的意图过滤器。
     *
     * 注意：卸载意图不带 MIME 类型，故**必须单独注册**——若与安装过滤器合并，
     * 过滤器上的 dataType 会导致卸载意图无法匹配。
     */
    private fun uninstallerFilter(): IntentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_DELETE)
        addAction(Intent.ACTION_UNINSTALL_PACKAGE)
        addCategory(Intent.CATEGORY_DEFAULT)
        addDataScheme("package")
    }

    /**
     * 注册本应用为默认安装器**与**默认卸载器（经开阳中枢 Device Owner 的持久首选活动）。
     */
    fun register(context: Context): Result<Unit> = runCatching {
        val admin = ownerAdmin(context) ?: error("需先激活「开阳中枢」（设备所有者）才能一键注册")
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activity = ComponentName(context, TianjiInstallActivity::class.java)
        dpm.addPersistentPreferredActivity(admin, installerFilter(), activity)
        dpm.addPersistentPreferredActivity(admin, uninstallerFilter(), activity)
    }

    /**
     * 取消本应用的默认安装器 / 卸载器身份。
     */
    fun unregister(context: Context): Result<Unit> = runCatching {
        val admin = ownerAdmin(context) ?: error("需先激活「开阳中枢」（设备所有者）才能取消注册")
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
    }

    /**
     * 无开阳中枢时的回退：唤起一次「打开方式」选择器，
     * 由用户在系统界面中勾选「始终」以指定默认安装器。
     */
    fun openSystemChooser(context: Context): Result<Unit> = runCatching {
        context.startActivity(installProbe().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
