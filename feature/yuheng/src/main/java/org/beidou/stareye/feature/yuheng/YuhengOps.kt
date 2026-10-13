package org.beidou.stareye.feature.yuheng

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import org.beidou.stareye.core.freeze.FreezeEvents
import org.beidou.stareye.feature.yuheng.YuhengCore.Result
import org.beidou.stareye.feature.yuheng.YuhengCore.admin
import org.beidou.stareye.feature.yuheng.YuhengCore.call
import org.beidou.stareye.feature.yuheng.YuhengCore.callFor
import org.beidou.stareye.feature.yuheng.YuhengCore.dpm

/**
 * 玉衡中枢 —— 操作执行层（自研实现）。
 *
 * 把声明式目录中的每个 id 映射到具体的系统 API。
 *
 * 注意：`DevicePolicyManager` 的绝大多数 getter/setter 都**要求传入 admin 组件**，
 * 且部分策略只有无参变体（如 `getMtePolicy()` / `isUsbDataSignalingEnabled()`）。
 */
object YuhengOps {

    private fun api(): Int = Build.VERSION.SDK_INT

    /** 「禁用锁屏」无对应 getter（`setKeyguardDisabled` 只返回是否成功），本地记录状态 */
    private const val KEY_KEYGUARD_DISABLED = "keyguard_disabled_state"

    // ═════════════════════ 一、系统开关 ═════════════════════

    fun systemRead(context: Context, id: String): Boolean = callFor(false) {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "camera_disabled" -> d.getCameraDisabled(a)
            "screen_capture_disabled" -> d.getScreenCaptureDisabled(a)
            "status_bar_disabled" -> if (api() >= 34) d.isStatusBarDisabled else false
            "keyguard_disabled" -> context.getSharedPreferences("yuheng_prefs", Context.MODE_PRIVATE)
                .getBoolean(KEY_KEYGUARD_DISABLED, false)
            "master_volume_muted" -> d.isMasterVolumeMuted(a)
            "usb_data_signaling" -> if (api() >= 31) d.isUsbDataSignalingEnabled else true
            "bluetooth_contact_sharing" -> if (api() >= 24) d.getBluetoothContactSharingDisabled(a) else false
            "common_criteria_mode" -> if (api() >= 30) d.isCommonCriteriaModeEnabled(a) else false
            "auto_time" -> if (api() >= 30) d.getAutoTimeEnabled(a) else false
            "auto_time_zone" -> if (api() >= 30) d.getAutoTimeZoneEnabled(a) else false
            "stay_on_plugged" -> Settings.Global.getString(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN) == "15"
            "backup_service" -> if (api() >= 26) d.isBackupServiceEnabled(a) else false
            "adb_enabled" -> Settings.Global.getString(context.contentResolver, Settings.Global.ADB_ENABLED) == "1"
            "usb_mass_storage" -> Settings.Global.getString(context.contentResolver, Settings.Global.USB_MASS_STORAGE_ENABLED) == "1"
            "wifi_config_lockdown" -> Settings.Global.getString(context.contentResolver, "wifi_device_owner_configs_lockdown") == "1"
            "skip_first_use_hints" -> Settings.Secure.getString(context.contentResolver, Settings.Secure.SKIP_FIRST_USE_HINTS) == "1"
            else -> false
        }
    }

    fun systemWrite(context: Context, id: String, value: Boolean): Result = call(entryTitle(id)) {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "camera_disabled" -> d.setCameraDisabled(a, value)
            "screen_capture_disabled" -> d.setScreenCaptureDisabled(a, value)
            "status_bar_disabled" -> if (api() >= 34) d.setStatusBarDisabled(a, value)
            "keyguard_disabled" -> {
                if (api() >= 23) {
                    val ok = d.setKeyguardDisabled(a, value)
                    check(ok) { "系统拒绝了该请求" }
                    context.getSharedPreferences("yuheng_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_KEYGUARD_DISABLED, value).apply()
                }
            }
            "master_volume_muted" -> d.setMasterVolumeMuted(a, value)
            "usb_data_signaling" -> if (api() >= 31) d.setUsbDataSignalingEnabled(value)
            "bluetooth_contact_sharing" -> if (api() >= 24) d.setBluetoothContactSharingDisabled(a, value)
            "common_criteria_mode" -> if (api() >= 30) d.setCommonCriteriaModeEnabled(a, value)
            "auto_time" -> if (api() >= 30) d.setAutoTimeEnabled(a, value)
            "auto_time_zone" -> if (api() >= 30) d.setAutoTimeZoneEnabled(a, value)
            "stay_on_plugged" -> d.setGlobalSetting(a, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (value) "15" else "0")
            "backup_service" -> if (api() >= 26) d.setBackupServiceEnabled(a, value)
            "adb_enabled" -> d.setGlobalSetting(a, Settings.Global.ADB_ENABLED, if (value) "1" else "0")
            "usb_mass_storage" -> d.setGlobalSetting(a, Settings.Global.USB_MASS_STORAGE_ENABLED, if (value) "1" else "0")
            "wifi_config_lockdown" -> d.setGlobalSetting(a, "wifi_device_owner_configs_lockdown", if (value) "1" else "0")
            "skip_first_use_hints" -> d.setSecureSetting(a, Settings.Secure.SKIP_FIRST_USE_HINTS, if (value) "1" else "0")
            else -> error("未知条目：$id")
        }
    }

    fun systemAction(context: Context, id: String): Result = call(entryTitle(id)) {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "lock_now" -> d.lockNow()
            "reboot" -> {
                if (api() < 24) error("需要 Android 7 及以上")
                d.reboot(a)
            }
            "bugreport" -> {
                if (api() < 24) error("需要 Android 7 及以上")
                check(d.requestBugreport(a)) { "请求被系统拒绝（可能过于频繁）" }
            }
            "wipe_data" -> {
                if (api() >= 29) d.wipeData(0, "由玉衡中枢发起的数据擦除")
                else d.wipeData(0)
            }
            else -> error("未知动作：$id")
        }
    }

    // ── 枚举型策略 ──

    fun enumOptions(id: String): List<String> = when (id) {
        "password_complexity" -> listOf("不控制", "低", "中", "高")
        "content_protection" -> listOf("不控制", "禁用", "启用")
        "permission_policy" -> listOf("设备默认", "自动授予", "自动拒绝")
        "mte_policy" -> listOf("设备默认", "启用", "禁用")
        "nearby_app_streaming", "nearby_notification_streaming" -> listOf("设备默认", "禁用", "启用", "同账号启用")
        "system_update_policy" -> listOf("不控制", "自动安装")
        "frp_policy" -> listOf("不控制", "启用恢复出厂保护")
        "password_quality" -> listOf("不指定", "低", "数字", "复杂数字", "字母", "字母数字", "高")
        "private_dns" -> listOf("自动", "关闭", "指定主机名")
        "wifi_min_security" -> listOf("开放网络", "WEP / WPA-PSK", "WPA2-PSK", "WPA-EAP", "WPA3-192")
        "credential_manager_policy" -> listOf("不控制", "黑名单", "白名单（含系统）", "白名单")
        "keyguard_features" -> listOf("全部启用", "自定义", "全部禁用")
        else -> emptyList()
    }

    fun enumIndex(context: Context, id: String): Int = callFor(0) {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "password_complexity" -> if (api() >= 31) d.requiredPasswordComplexity else 0
            "content_protection" -> if (api() >= 35) d.getContentProtectionPolicy(a) else 0
            "permission_policy" -> d.getPermissionPolicy(a)
            "mte_policy" -> if (api() >= 34) d.mtePolicy else 0
            "nearby_app_streaming" -> if (api() >= 31) d.nearbyAppStreamingPolicy else 0
            "nearby_notification_streaming" -> if (api() >= 31) d.nearbyNotificationStreamingPolicy else 0
            "system_update_policy" -> if (d.systemUpdatePolicy == null) 0 else 1
            "frp_policy" -> if (api() >= 30 && d.getFactoryResetProtectionPolicy(a)?.isFactoryResetProtectionEnabled == true) 1 else 0
            "password_quality" -> d.getPasswordQuality(a)
            "private_dns" -> if (api() >= 29) d.getGlobalPrivateDnsMode(a) else 0
            "wifi_min_security" -> if (api() >= 33) d.minimumRequiredWifiSecurityLevel else 0
            "keyguard_features" -> d.getKeyguardDisabledFeatures(a)
            else -> 0
        }
    }

    fun enumWrite(context: Context, id: String, index: Int): Result = call(entryTitle(id)) {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "password_complexity" -> if (api() >= 31) d.requiredPasswordComplexity = index
            "content_protection" -> if (api() >= 35) d.setContentProtectionPolicy(a, index)
            "permission_policy" -> d.setPermissionPolicy(a, index)
            "mte_policy" -> if (api() >= 34) d.mtePolicy = index
            "nearby_app_streaming" -> if (api() >= 31) d.setNearbyAppStreamingPolicy(index)
            "nearby_notification_streaming" -> if (api() >= 31) d.setNearbyNotificationStreamingPolicy(index)
            "system_update_policy" -> d.setSystemUpdatePolicy(
                a,
                if (index == 0) null else SystemUpdatePolicy.createAutomaticInstallPolicy(),
            )
            "frp_policy" -> if (api() >= 30) {
                if (index == 0) {
                    d.setFactoryResetProtectionPolicy(a, null)
                } else {
                    val policy = FactoryResetProtectionPolicy.Builder()
                        .setFactoryResetProtectionEnabled(true)
                        .build()
                    d.setFactoryResetProtectionPolicy(a, policy)
                }
            }
            "password_quality" -> d.setPasswordQuality(a, index)
            "private_dns" -> if (api() >= 29) {
                if (index == 0) d.setGlobalPrivateDnsModeOpportunistic(a)
                else d.setGlobalPrivateDnsModeSpecifiedHost(a, PRIVATE_DNS_HOST)
            }
            "wifi_min_security" -> if (api() >= 33) d.minimumRequiredWifiSecurityLevel = index
            "keyguard_features" -> d.setKeyguardDisabledFeatures(a, index)
            else -> error("未知枚举项：$id")
        }
    }

    private const val PRIVATE_DNS_HOST = "dns.google"

    // ── 文本型设置 ──

    fun textValue(context: Context, id: String): String = callFor("") {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "organization_name" -> d.getOrganizationName(a)?.toString() ?: ""
            "lock_screen_info" -> d.deviceOwnerLockScreenInfo?.toString() ?: ""
            "short_support_message" -> d.getShortSupportMessage(a)?.toString() ?: ""
            "long_support_message" -> d.getLongSupportMessage(a)?.toString() ?: ""
            "password_expiration" -> (d.getPasswordExpirationTimeout(a) / 86_400_000L).toString()
            "password_history" -> d.getPasswordHistoryLength(a).toString()
            "max_failed_passwords" -> d.getMaximumFailedPasswordsForWipe(a).toString()
            "lock_timeout" -> (d.getMaximumTimeToLock(a) / 1000L).toString()
            "strong_auth_timeout" -> (d.getRequiredStrongAuthTimeout(a) / 1000L).toString()
            "user_affiliation" -> d.getAffiliationIds(a).joinToString(",")
            "session_message" -> d.getStartUserSessionMessage(a)?.toString() ?: ""
            "wp_max_time_off" -> if (api() >= 30) d.getManagedProfileMaximumTimeOff(a).toString() else "0"
            "reset_password" -> ""
            else -> ""
        }
    }

    fun textWrite(context: Context, id: String, value: String): Result = call(entryTitle(id)) {
        val d = dpm(context)
        val a = admin(context)
        when (id) {
            "organization_name" -> d.setOrganizationName(a, value)
            "lock_screen_info" -> d.setDeviceOwnerLockScreenInfo(a, value)
            "short_support_message" -> d.setShortSupportMessage(a, value)
            "long_support_message" -> d.setLongSupportMessage(a, value)
            "password_expiration" -> d.setPasswordExpirationTimeout(a, value.toLongOrNull()?.times(86_400_000L) ?: 0L)
            "password_history" -> d.setPasswordHistoryLength(a, value.toIntOrNull() ?: 0)
            "max_failed_passwords" -> d.setMaximumFailedPasswordsForWipe(a, value.toIntOrNull() ?: 0)
            "lock_timeout" -> d.setMaximumTimeToLock(a, value.toLongOrNull()?.times(1000L) ?: 0L)
            "strong_auth_timeout" -> d.setRequiredStrongAuthTimeout(a, value.toLongOrNull()?.times(1000L) ?: 0L)
            "user_affiliation" -> d.setAffiliationIds(
                a,
                value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            )
            "session_message" -> {
                d.setStartUserSessionMessage(a, value)
                d.setEndUserSessionMessage(a, value)
            }
            "wp_max_time_off" -> if (api() >= 30) d.setManagedProfileMaximumTimeOff(a, value.toLongOrNull() ?: 0L)
            "reset_password" -> {
                check(value.length >= 4) { "密码长度需 ≥ 4" }
                val ok = if (api() >= 26) {
                    val token = context.getSharedPreferences("yuheng_prefs", Context.MODE_PRIVATE)
                        .getString("reset_token", null)?.toByteArray(Charsets.UTF_8)
                    if (token != null) d.resetPasswordWithToken(a, value, token, 0)
                    else d.resetPassword(value, 0)
                } else {
                    d.resetPassword(value, 0)
                }
                check(ok) { "系统拒绝了该密码（可能不满足当前密码策略）" }
            }
            else -> error("未知文本项：$id")
        }
    }

    // ═════════════════════ 二、用户限制 ═════════════════════

    fun restrictionEnabled(context: Context, key: String): Boolean = callFor(false) {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        um.userRestrictions?.getBoolean(key) ?: false
    }

    fun restrictionSet(context: Context, key: String, restricted: Boolean): Result = call("用户限制") {
        val d = dpm(context)
        val a = admin(context)
        if (restricted) d.addUserRestriction(a, key) else d.clearUserRestriction(a, key)
    }

    // ═════════════════════ 三、应用管理 ═════════════════════

    fun appState(context: Context, op: String, pkg: String): Boolean = callFor(false) {
        val d = dpm(context)
        val a = admin(context)
        when (op) {
            "app_suspend" -> if (api() >= 24) d.isPackageSuspended(a, pkg) else false
            "app_hide" -> d.isApplicationHidden(a, pkg)
            "app_block_uninstall" -> d.isUninstallBlocked(a, pkg)
            "app_disable_user_control" -> if (api() >= 30) pkg in d.getUserControlDisabledPackages(a) else false
            "app_disable_metered" -> if (api() >= 28) pkg in d.getMeteredDataDisabledPackages(a) else false
            else -> false
        }
    }

    fun appToggle(context: Context, op: String, pkg: String, on: Boolean): Result {
        val result = call("$op $pkg") {
            val d = dpm(context)
            val a = admin(context)
            when (op) {
                "app_suspend" -> if (api() >= 24) d.setPackagesSuspended(a, arrayOf(pkg), on)
                "app_hide" -> d.setApplicationHidden(a, pkg, on)
                "app_block_uninstall" -> d.setUninstallBlocked(a, pkg, on)
                "app_disable_user_control" -> if (api() >= 30) {
                    val cur = d.getUserControlDisabledPackages(a).toMutableSet()
                    if (on) cur.add(pkg) else cur.remove(pkg)
                    d.setUserControlDisabledPackages(a, cur.toList())
                }
                "app_disable_metered" -> if (api() >= 28) {
                    val cur = d.getMeteredDataDisabledPackages(a).toMutableSet()
                    if (on) cur.add(pkg) else cur.remove(pkg)
                    d.setMeteredDataDisabledPackages(a, cur.toList())
                }
                else -> error("未知应用操作：$op")
            }
        }
        // 挂起 / 隐藏属冻结语义：广播事件使天璇与瑶光的界面同步刷新
        if (result.isOk && (op == "app_suspend" || op == "app_hide")) {
            FreezeEvents.notifyChanged(pkg)
        }
        return result
    }

    fun clearAppData(context: Context, pkg: String): Result = call("清除 $pkg 的数据") {
        if (api() < 28) error("需要 Android 9 及以上")
        dpm(context).clearApplicationUserData(
            admin(context),
            pkg,
            context.mainExecutor,
            DevicePolicyManager.OnClearApplicationUserDataListener { _, _ -> },
        )
    }

    fun uninstallApp(context: Context, pkg: String): Result = call("卸载 $pkg") {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun setPermissionState(context: Context, pkg: String, permission: String, state: Int): Result =
        call("设置权限") {
            if (api() < 23) error("需要 Android 6 及以上")
            check(dpm(context).setPermissionGrantState(admin(context), pkg, permission, state)) {
                "系统拒绝了该权限变更"
            }
        }

    fun permissionState(context: Context, pkg: String, permission: String): Int = callFor(
        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT,
    ) {
        if (api() < 23) DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        else dpm(context).getPermissionGrantState(admin(context), pkg, permission)
    }

    fun requestedPermissions(context: Context, pkg: String): List<String> = callFor(emptyList()) {
        context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList() ?: emptyList()
    }

    /** 全部「危险权限」去重清单 */
    fun dangerousPermissions(context: Context): List<Pair<String, String>> = callFor(emptyList()) {
        val pm = context.packageManager
        val out = LinkedHashMap<String, String>()
        @Suppress("DEPRECATION")
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).forEach { pi ->
            pi.requestedPermissions?.forEach { perm ->
                val info = callFor<android.content.pm.PermissionInfo?>(null) { pm.getPermissionInfo(perm, 0) }
                if (info != null && info.protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) {
                    out[perm] = callFor(perm.substringAfterLast('.')) { info.loadLabel(pm).toString() }
                }
            }
        }
        out.entries.map { it.key to it.value }
    }

    fun appRestrictions(context: Context, pkg: String): Bundle = callFor(Bundle()) {
        dpm(context).getApplicationRestrictions(admin(context), pkg) ?: Bundle()
    }

    fun setAppRestrictions(context: Context, pkg: String, bundle: Bundle): Result =
        call("写入托管配置") { dpm(context).setApplicationRestrictions(admin(context), pkg, bundle) }

    /** 列出全部应用（供选择器复用） */
    fun installedApps(context: Context, includeSystem: Boolean): List<Triple<String, String, Boolean>> =
        callFor(emptyList()) {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .filter {
                    includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .map { ai ->
                    Triple(
                        ai.packageName,
                        callFor(ai.packageName) { ai.loadLabel(pm).toString() },
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedBy { it.second.lowercase() }
        }

    // ═════════════════════ 四、用户管理 ═════════════════════

    fun secondaryUsers(context: Context): List<UserHandle> = callFor(emptyList()) {
        if (api() < 28) emptyList() else dpm(context).getSecondaryUsers(admin(context)) ?: emptyList()
    }

    fun userOperation(context: Context, op: String, user: UserHandle): Result = call("用户操作") {
        val d = dpm(context)
        val a = admin(context)
        when (op) {
            "start" -> {
                if (api() < 28) error("需要 Android 9 及以上")
                d.startUserInBackground(a, user)
            }
            "switch" -> d.switchUser(a, user)
            "stop" -> {
                if (api() < 28) error("需要 Android 9 及以上")
                d.stopUser(a, user)
            }
            "remove" -> {
                check(d.removeUser(a, user)) { "删除失败（当前用户或系统用户不可删除）" }
            }
            else -> error("未知用户操作：$op")
        }
    }

    fun createUser(context: Context, name: String, ephemeral: Boolean, skipSetup: Boolean): Result =
        call("创建用户") {
            if (api() < 24) error("需要 Android 7 及以上")
            var flags = 0
            if (skipSetup) flags = flags or DevicePolicyManager.SKIP_SETUP_WIZARD
            if (ephemeral && api() >= 28) flags = flags or DevicePolicyManager.MAKE_USER_EPHEMERAL
            val a = admin(context)
            val handle = dpm(context).createAndManageUser(a, name, a, null, flags)
            check(handle != null) { "创建失败" }
        }

    fun userIdOf(user: UserHandle): Int = callFor(-1) {
        val f = UserHandle::class.java.getDeclaredField("mHandle")
        f.isAccessible = true
        f.getInt(user)
    }

    fun logoutUser(context: Context): Result = call("注销用户") {
        if (api() < 28) error("需要 Android 9 及以上")
        dpm(context).logoutUser(admin(context))
    }

    fun setLogoutEnabled(context: Context, enabled: Boolean): Result = call("允许注销") {
        if (api() < 28) error("需要 Android 9 及以上")
        dpm(context).setLogoutEnabled(admin(context), enabled)
    }

    fun isLogoutEnabled(context: Context): Boolean = callFor(false) {
        if (api() < 28) false else dpm(context).isLogoutEnabled
    }

    // ═════════════════════ 五、网络 ═════════════════════

    fun wifiEnabled(context: Context): Boolean = callFor(false) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wm.isWifiEnabled
    }

    fun setWifiEnabled(context: Context, on: Boolean): Result = call("Wi-Fi 开关") {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        wm.isWifiEnabled = on
    }

    fun configuredWifi(context: Context): List<Triple<Int, String, Boolean>> = callFor(emptyList()) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION", "MissingPermission")
        wm.configuredNetworks?.map { cfg ->
            Triple(cfg.networkId, cfg.SSID ?: "(隐藏网络)", cfg.status == android.net.wifi.WifiConfiguration.Status.ENABLED)
        } ?: emptyList()
    }

    fun removeWifi(context: Context, networkId: Int): Result = call("删除网络") {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION", "MissingPermission")
        check(wm.removeNetwork(networkId)) { "删除失败" }
    }

    fun wifiMac(context: Context): String = callFor("") {
        if (api() >= 24) dpm(context).getWifiMacAddress(admin(context)) ?: "" else ""
    }

    fun setPrivateDns(host: String): String = host

    // ═════════════════════ 六、密码 ═════════════════════

    fun passwordInfo(context: Context): List<Pair<String, String>> = callFor(emptyList()) {
        val d = dpm(context)
        val a = admin(context)
        buildList {
            add("密码复杂度" to if (api() >= 31) complexityLabel(d.requiredPasswordComplexity) else "—")
            add("密码质量" to qualityLabel(callFor(0) { d.getPasswordQuality(a) }))
            add("密码是否足够" to if (callFor(false) { d.isActivePasswordSufficient }) "是" else "否")
            add("最大失败次数" to callFor(0) { d.getMaximumFailedPasswordsForWipe(a) }.toString())
            add("密码历史长度" to callFor(0) { d.getPasswordHistoryLength(a) }.toString())
            add("密码有效期" to "${callFor(0L) { d.getPasswordExpirationTimeout(a) } / 86_400_000L} 天")
            add("锁屏超时" to "${callFor(0L) { d.getMaximumTimeToLock(a) } / 1000} 秒")
            add("强认证超时" to "${callFor(0L) { d.getRequiredStrongAuthTimeout(a) } / 1000} 秒")
            if (api() >= 26) {
                add("重置令牌" to if (callFor(false) { d.isResetPasswordTokenActive(a) }) "已激活" else "未激活")
            }
        }
    }

    private fun complexityLabel(v: Int): String = when (v) {
        DevicePolicyManager.PASSWORD_COMPLEXITY_NONE -> "无"
        DevicePolicyManager.PASSWORD_COMPLEXITY_LOW -> "低"
        DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM -> "中"
        DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH -> "高"
        else -> "—"
    }

    private fun qualityLabel(v: Int): String = when (v) {
        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED -> "不指定"
        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING -> "低"
        DevicePolicyManager.PASSWORD_QUALITY_NUMERIC -> "数字"
        DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX -> "复杂数字"
        DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC -> "字母"
        DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC -> "字母数字"
        DevicePolicyManager.PASSWORD_QUALITY_COMPLEX -> "高"
        else -> "—"
    }

    fun setResetToken(context: Context, token: String?): Result = call("重置密码令牌") {
        if (api() < 26) error("需要 Android 8 及以上")
        val d = dpm(context)
        val a = admin(context)
        if (token == null) {
            d.clearResetPasswordToken(a)
            context.getSharedPreferences("yuheng_prefs", Context.MODE_PRIVATE)
                .edit().remove("reset_token").apply()
        } else {
            check(d.setResetPasswordToken(a, token.toByteArray(Charsets.UTF_8))) { "设置被拒绝" }
            context.getSharedPreferences("yuheng_prefs", Context.MODE_PRIVATE)
                .edit().putString("reset_token", token).apply()
        }
    }

    // ═════════════════════ 七、CA 证书 ═════════════════════

    fun installedCaCerts(context: Context): List<String> = callFor(emptyList()) {
        val factory = java.security.cert.CertificateFactory.getInstance("X.509")
        dpm(context).getInstalledCaCerts(admin(context)).mapNotNull { der ->
            callFor<String?>(null) {
                val cert = factory.generateCertificate(java.io.ByteArrayInputStream(der))
                (cert as java.security.cert.X509Certificate).subjectX500Principal.name
            }
        }
    }

    fun installCaCert(context: Context, der: ByteArray): Result = call("安装 CA 证书") {
        check(dpm(context).installCaCert(admin(context), der)) { "证书安装被拒绝" }
    }

    fun uninstallAllCaCerts(context: Context): Result = call("卸载全部 CA 证书") {
        dpm(context).uninstallAllUserCaCerts(admin(context))
    }

    // ═════════════════════ 八、日志 ═════════════════════

    fun securityLoggingEnabled(context: Context): Boolean = callFor(false) {
        if (api() < 26) false else dpm(context).isSecurityLoggingEnabled(admin(context))
    }

    fun setSecurityLogging(context: Context, on: Boolean): Result = call("安全日志开关") {
        if (api() < 26) error("需要 Android 8 及以上")
        dpm(context).setSecurityLoggingEnabled(admin(context), on)
    }

    fun retrieveSecurityLogs(context: Context): List<String> = callFor(emptyList()) {
        if (api() < 26) emptyList()
        else dpm(context).retrieveSecurityLogs(admin(context))
            ?.map { event ->
                // 注意：SDK 桩里 getData() 声明为 Object，运行时才是 Object[]，故需转型
                val raw = event.data
                val data = when (raw) {
                    is Array<*> -> raw.joinToString(" ") { it?.toString() ?: "" }
                    null -> ""
                    else -> raw.toString()
                }
                "时间=${event.timeNanos} 事件=${event.tag} $data"
            }
            ?: emptyList()
    }

    fun networkLoggingEnabled(context: Context): Boolean = callFor(false) {
        if (api() < 26) false else dpm(context).isNetworkLoggingEnabled(admin(context))
    }

    fun setNetworkLogging(context: Context, on: Boolean): Result = call("网络日志开关") {
        if (api() < 26) error("需要 Android 8 及以上")
        dpm(context).setNetworkLoggingEnabled(admin(context), on)
    }

    // ── 工具 ──

    /** 由 id 反查标题（用于操作结果提示） */
    fun entryTitle(id: String): String =
        YuhengSystemCatalog.all.firstOrNull { it.id == id }?.title
            ?: YuhengPasswordCatalog.groups.flatMap { it.entries }.firstOrNull { it.id == id }?.title
            ?: YuhengUserCatalog.actions.firstOrNull { it.id == id }?.title
            ?: YuhengUserCatalog.workProfile.firstOrNull { it.id == id }?.title
            ?: YuhengNetworkCatalog.groups.flatMap { it.entries }.firstOrNull { it.id == id }?.title
            ?: YuhengAppCatalog.batchOps.firstOrNull { it.id == id }?.title
            ?: id

    /** 供设备信息页使用 */
    fun storageEncryptionStatus(context: Context): Int =
        callFor(-1) { dpm(context).storageEncryptionStatus }

    fun activeAdmins(context: Context): List<ComponentName> =
        callFor(emptyList()) { dpm(context).activeAdmins ?: emptyList() }

    fun enrollmentSpecificId(context: Context): String =
        callFor("") { if (api() >= 31) dpm(context).enrollmentSpecificId ?: "" else "" }

    fun isAffiliatedUser(context: Context): Boolean =
        callFor(false) { api() >= 28 && dpm(context).isAffiliatedUser }

    fun lockTaskPackages(context: Context): List<String> =
        callFor(emptyList()) { dpm(context).getLockTaskPackages(admin(context))?.toList() ?: emptyList() }

    fun setLockTaskPackages(context: Context, packages: List<String>): Result =
        call("设置锁定任务白名单") {
            dpm(context).setLockTaskPackages(admin(context), packages.toTypedArray())
        }

    fun accountTypesManagementDisabled(context: Context): List<String> =
        callFor(emptyList()) { dpm(context).accountTypesWithManagementDisabled?.toList() ?: emptyList() }

    fun setAccountManagementDisabled(context: Context, accountType: String, disabled: Boolean): Result =
        call("账号管理限制") {
            dpm(context).setAccountManagementDisabled(admin(context), accountType, disabled)
        }
}
