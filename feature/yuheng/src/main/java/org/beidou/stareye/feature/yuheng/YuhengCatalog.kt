package org.beidou.stareye.feature.yuheng

import android.os.Build
import android.os.UserManager

/**
 * 玉衡中枢 —— 声明式功能目录（自研实现，零拷贝）。
 *
 * 把 OwnDroid 的全部功能抽象为「条目 + 类型」，由通用渲染器与通用执行器处理，
 * 从而在覆盖完整功能面的同时避免为每个条目单独编写界面与逻辑。
 */

/** 条目类型 */
enum class EntryKind {
    /** 开关（读写布尔） */
    TOGGLE,

    /** 动作（点按执行，如重启、锁屏） */
    ACTION,

    /** 枚举单选（如密码复杂度、策略模式） */
    ENUM,

    /** 文本输入 */
    TEXT,

    /** 进入专用子页面 */
    SCREEN,
}

/** 功能条目 */
data class FeatureEntry(
    val id: String,
    val title: String,
    val detail: String = "",
    val kind: EntryKind = EntryKind.TOGGLE,
    val minApi: Int = 1,
    /** 危险操作（默认隐藏在「显示危险功能」开关之后） */
    val danger: Boolean = false,
    /** 需要 Device Owner（否则仅 Profile Owner 时隐藏） */
    val deviceOnly: Boolean = false,
)

/** 功能分组 */
data class FeatureGroup(val title: String, val entries: List<FeatureEntry>)

// ───────────────────────── 一、系统 ─────────────────────────

object YuhengSystemCatalog {

    val groups: List<FeatureGroup> = listOf(
        FeatureGroup(
            "设备限制",
            listOf(
                FeatureEntry("camera_disabled", "禁用摄像头", "禁止使用设备摄像头", minApi = 21),
                FeatureEntry("screen_capture_disabled", "禁止截屏", "禁止截屏与录屏", minApi = 21),
                FeatureEntry("status_bar_disabled", "禁用状态栏", "禁止下拉状态栏", minApi = 34),
                FeatureEntry("keyguard_disabled", "禁用锁屏", "允许无锁屏直接进入设备", minApi = 23),
                FeatureEntry("master_volume_muted", "全局静音", "主音量强制静音", minApi = 21),
                FeatureEntry("usb_data_signaling", "USB 数据信令", "关闭后 USB 仅供电不传数据", minApi = 31),
                FeatureEntry("bluetooth_contact_sharing", "禁用蓝牙联系人共享", "禁止通过蓝牙共享联系人", minApi = 24),
                FeatureEntry("common_criteria_mode", "通用准则模式", "启用 Common Criteria 安全认证模式", minApi = 30),
            ),
        ),
        FeatureGroup(
            "时间与电源",
            listOf(
                FeatureEntry("auto_time", "自动时间", "由网络自动校时", minApi = 30),
                FeatureEntry("auto_time_zone", "自动时区", "由网络自动确定时区", minApi = 30),
                FeatureEntry("stay_on_plugged", "插电常亮", "充电时屏幕保持常亮", minApi = 21),
                FeatureEntry("backup_service", "备份服务", "启用系统备份服务", minApi = 26),
            ),
        ),
        FeatureGroup(
            "系统设置开关",
            listOf(
                FeatureEntry("adb_enabled", "ADB 调试", "开启 USB 调试（全局设置）", minApi = 21, danger = true),
                FeatureEntry("usb_mass_storage", "USB 大容量存储", "开启 USB 存储模式", minApi = 21, danger = true),
                FeatureEntry("wifi_config_lockdown", "锁定 Wi-Fi 配置", "禁止修改管理员配置的 Wi-Fi 网络", minApi = 30, deviceOnly = true),
                FeatureEntry("skip_first_use_hints", "跳过首次使用提示", "跳过应用的首次使用引导", minApi = 21),
            ),
        ),
        FeatureGroup(
            "策略",
            listOf(
                FeatureEntry("password_complexity", "必需密码复杂度", "设定设备所需的最低密码强度", kind = EntryKind.ENUM, minApi = 31),
                FeatureEntry("content_protection", "内容保护策略", "控制剪贴板等内容的保护级别", kind = EntryKind.ENUM, minApi = 35),
                FeatureEntry("permission_policy", "权限策略", "自动授予或自动拒绝应用的权限请求", kind = EntryKind.ENUM, minApi = 23),
                FeatureEntry("mte_policy", "MTE 策略", "内存标记扩展策略", kind = EntryKind.ENUM, minApi = 34, deviceOnly = true),
                FeatureEntry("nearby_app_streaming", "近场应用流媒体策略", "控制向附近设备投射应用", kind = EntryKind.ENUM, minApi = 31),
                FeatureEntry("nearby_notification_streaming", "近场通知流媒体策略", "控制向附近设备投射通知", kind = EntryKind.ENUM, minApi = 31),
                FeatureEntry("system_update_policy", "系统更新策略", "自动更新的时机与推迟", kind = EntryKind.ENUM, minApi = 21, deviceOnly = true),
                FeatureEntry("frp_policy", "恢复出厂保护策略", "设定 FRP 允许的账号", kind = EntryKind.ENUM, minApi = 30),
            ),
        ),
        FeatureGroup(
            "组织信息",
            listOf(
                FeatureEntry("organization_name", "组织名称", "显示在系统设置中的组织名", kind = EntryKind.TEXT, minApi = 24),
                FeatureEntry("lock_screen_info", "锁屏信息", "显示在锁屏界面的提示文本", kind = EntryKind.TEXT, minApi = 24),
                FeatureEntry("short_support_message", "短支持消息", "用户操作被拒绝时的提示", kind = EntryKind.TEXT, minApi = 24),
                FeatureEntry("long_support_message", "长支持消息", "详细的帮助说明", kind = EntryKind.TEXT, minApi = 24),
            ),
        ),
        FeatureGroup(
            "专用管理",
            listOf(
                FeatureEntry("lock_task", "锁定任务模式", "Kiosk 单一/多应用锁定", kind = EntryKind.SCREEN, minApi = 21, deviceOnly = true),
                FeatureEntry("ca_certificates", "CA 证书管理", "安装 / 卸载受信任的 CA 证书", kind = EntryKind.SCREEN, minApi = 21),
                FeatureEntry("security_log", "安全日志", "设备安全事件日志的采集与导出", kind = EntryKind.SCREEN, minApi = 26, deviceOnly = true),
                FeatureEntry("device_info", "设备与策略信息", "加密状态、活动管理员、组织 ID 等", kind = EntryKind.SCREEN, minApi = 21),
                FeatureEntry("account_management", "账号管理限制", "禁止对指定账号类型进行管理", kind = EntryKind.SCREEN, minApi = 21),
                FeatureEntry("hardware_monitor", "硬件监控", "CPU / GPU / 电池温度与占用", kind = EntryKind.SCREEN, minApi = 24, deviceOnly = true),
            ),
        ),
        FeatureGroup(
            "危险操作",
            listOf(
                FeatureEntry("lock_now", "立即锁屏", "立刻锁定设备", kind = EntryKind.ACTION, minApi = 21),
                FeatureEntry("reboot", "重启设备", "重启设备（需 Device Owner）", kind = EntryKind.ACTION, minApi = 24, deviceOnly = true, danger = true),
                FeatureEntry("bugreport", "请求错误报告", "生成并请求设备错误报告", kind = EntryKind.ACTION, minApi = 24, deviceOnly = true, danger = true),
                FeatureEntry("wipe_data", "擦除数据", "恢复出厂设置，不可撤销", kind = EntryKind.ACTION, minApi = 21, danger = true),
            ),
        ),
    )

    val all: List<FeatureEntry> = groups.flatMap { it.entries }
}

// ───────────────────────── 二、用户限制（69 项） ─────────────────────────

/**
 * 用户限制项。
 *
 * @param key `UserManager.DISALLOW_*` 常量值
 * @param group 界面分组
 */
data class RestrictionItem(
    val key: String,
    val title: String,
    val minApi: Int = 23,
    val group: String,
)

object YuhengRestrictions {

    private fun u(key: String) = key

    val all: List<RestrictionItem> = listOf(
        // —— 网络（20）——
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS), "配置移动数据", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_WIFI), "配置 Wi-Fi", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_DATA_ROAMING), "数据漫游", 24, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CELLULAR_2G), "使用 2G 网络", 34, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_ULTRA_WIDEBAND_RADIO), "超宽频段无线电", 34, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_ADD_WIFI_CONFIG), "添加 Wi-Fi 配置", 33, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CHANGE_WIFI_STATE), "修改 Wi-Fi 状态", 33, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_WIFI_DIRECT), "Wi-Fi 直连", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_WIFI_TETHERING), "Wi-Fi 共享", 33, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI), "分享管理员配置的 Wi-Fi", 33, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_NETWORK_RESET), "重置网络设置", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_TETHERING), "配置网络共享", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_VPN), "配置 VPN", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_PRIVATE_DNS), "配置私人 DNS", 29, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_AIRPLANE_MODE), "飞行模式", 28, "网络"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS), "配置小区广播", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_SMS), "发送短信", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_OUTGOING_CALLS), "拨出电话", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_SIM_GLOBALLY), "下载 eSIM", group = "网络"),
        RestrictionItem(u(UserManager.DISALLOW_THREAD_NETWORK), "Thread 网络", 36, "网络"),

        // —— 连接（9）——
        RestrictionItem(u(UserManager.DISALLOW_BLUETOOTH), "蓝牙", 26, "连接"),
        RestrictionItem(u(UserManager.DISALLOW_BLUETOOTH_SHARING), "蓝牙共享", 26, "连接"),
        RestrictionItem(u(UserManager.DISALLOW_SHARE_LOCATION), "分享位置信息", group = "连接"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_LOCATION), "配置位置信息", 28, "连接"),
        RestrictionItem(u(UserManager.DISALLOW_NEAR_FIELD_COMMUNICATION_RADIO), "NFC", 35, "连接"),
        RestrictionItem(u(UserManager.DISALLOW_OUTGOING_BEAM), "Beam 发送", group = "连接"),
        RestrictionItem(u(UserManager.DISALLOW_USB_FILE_TRANSFER), "USB 文件传输", group = "连接"),
        RestrictionItem(u(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA), "挂载物理媒体", group = "连接"),
        RestrictionItem(u(UserManager.DISALLOW_PRINTING), "打印", 28, "连接"),

        // —— 应用（7）——
        RestrictionItem(u(UserManager.DISALLOW_INSTALL_APPS), "安装应用", group = "应用"),
        RestrictionItem(u(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY), "安装未知来源（全局）", 29, "应用"),
        RestrictionItem(u(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES), "安装未知来源", group = "应用"),
        RestrictionItem(u(UserManager.DISALLOW_UNINSTALL_APPS), "卸载应用", group = "应用"),
        RestrictionItem(u(UserManager.DISALLOW_APPS_CONTROL), "控制应用", group = "应用"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_DEFAULT_APPS), "修改默认应用", 34, "应用"),
        RestrictionItem(u(UserManager.ENSURE_VERIFY_APPS), "禁用应用验证", group = "应用"),

        // —— 媒体（7）——
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_BRIGHTNESS), "调整亮度", 28, "媒体"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT), "修改屏幕超时", 28, "媒体"),
        RestrictionItem(u(UserManager.DISALLOW_AMBIENT_DISPLAY), "息屏显示", 28, "媒体"),
        RestrictionItem(u(UserManager.DISALLOW_ADJUST_VOLUME), "调整音量", group = "媒体"),
        RestrictionItem(u(UserManager.DISALLOW_UNMUTE_MICROPHONE), "取消麦克风静音", group = "媒体"),
        RestrictionItem(u(UserManager.DISALLOW_CAMERA_TOGGLE), "切换摄像头权限", 31, "媒体"),
        RestrictionItem(u(UserManager.DISALLOW_MICROPHONE_TOGGLE), "切换麦克风权限", 31, "媒体"),

        // —— 用户（10）——
        RestrictionItem(u(UserManager.DISALLOW_ADD_USER), "添加用户", group = "用户"),
        RestrictionItem(u(UserManager.DISALLOW_REMOVE_USER), "移除用户", group = "用户"),
        RestrictionItem(u(UserManager.DISALLOW_USER_SWITCH), "切换用户", 28, "用户"),
        RestrictionItem(u(UserManager.DISALLOW_ADD_MANAGED_PROFILE), "创建工作资料", 26, "用户"),
        RestrictionItem(u(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE), "删除工作资料", 26, "用户"),
        RestrictionItem(u(UserManager.DISALLOW_ADD_PRIVATE_PROFILE), "创建私密空间", 35, "用户"),
        RestrictionItem(u(UserManager.DISALLOW_SET_USER_ICON), "修改用户头像", 24, "用户"),
        RestrictionItem(u(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE), "跨用户复制粘贴", group = "用户"),
        RestrictionItem(u(UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE), "分享至工作应用", 28, "用户"),
        RestrictionItem(u(UserManager.DISALLOW_UNIFIED_PASSWORD), "个人与工作密码相同", 28, "用户"),

        // —— 其他（16）——
        RestrictionItem(u(UserManager.DISALLOW_AUTOFILL), "自动填充", 26, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_CREDENTIALS), "配置凭据", group = "其他"),
        RestrictionItem(u(UserManager.DISALLOW_CONTENT_CAPTURE), "内容捕获", 29, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_CONTENT_SUGGESTIONS), "内容建议", 29, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_ASSIST_CONTENT), "辅助内容", 35, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_CREATE_WINDOWS), "创建窗口", group = "其他"),
        RestrictionItem(u(UserManager.DISALLOW_SET_WALLPAPER), "更换壁纸", 24, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_GRANT_ADMIN), "启用设备管理器", 34, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_FUN), "娱乐功能", group = "其他"),
        RestrictionItem(u(UserManager.DISALLOW_MODIFY_ACCOUNTS), "修改账号设置", group = "其他"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_LOCALE), "修改语言", 28, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_CONFIG_DATE_TIME), "修改日期时间", 28, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS), "系统报错对话框", 28, "其他"),
        RestrictionItem(u(UserManager.DISALLOW_FACTORY_RESET), "恢复出厂设置", group = "其他"),
        RestrictionItem(u(UserManager.DISALLOW_SAFE_BOOT), "安全模式", group = "其他"),
        RestrictionItem(u(UserManager.DISALLOW_DEBUGGING_FEATURES), "调试功能", group = "其他"),
    )

    val groupNames: List<String> = all.map { it.group }.distinct()
}

// ───────────────────────── 三、应用管理 ─────────────────────────

object YuhengAppCatalog {

    /** 应用批量操作（对应 OwnDroid 的批量开关） */
    val batchOps: List<FeatureEntry> = listOf(
        FeatureEntry("app_suspend", "挂起应用", "挂起后应用不可启动、通知被抑制", minApi = 24),
        FeatureEntry("app_hide", "隐藏应用", "从桌面与应用列表隐藏", minApi = 21),
        FeatureEntry("app_block_uninstall", "阻止卸载", "禁止用户卸载该应用", minApi = 21),
        FeatureEntry("app_disable_user_control", "禁用用户控制", "禁止用户强行停止或清除数据", minApi = 30),
        FeatureEntry("app_disable_metered", "禁用计量数据", "禁止该应用使用按量计费网络", minApi = 28),
    )

    /** 设备级应用策略 */
    val policies: List<FeatureEntry> = listOf(
        FeatureEntry("permitted_input_methods", "允许的输入法", "限定可用的输入法", kind = EntryKind.SCREEN, minApi = 21),
        FeatureEntry("permitted_accessibility", "允许的无障碍服务", "限定可用的无障碍服务", kind = EntryKind.SCREEN, minApi = 21),
        FeatureEntry("keep_uninstalled", "保留已卸载应用", "卸载后保留数据与缓存", kind = EntryKind.SCREEN, minApi = 28, deviceOnly = true),
        FeatureEntry("credential_manager_policy", "凭据管理器策略", "限定可用凭据提供方", kind = EntryKind.ENUM, minApi = 34, deviceOnly = true),
        FeatureEntry("cross_profile_packages", "跨资料应用", "允许在个人与工作资料间互通", kind = EntryKind.SCREEN, minApi = 30),
    )

    /** 单应用可执行的操作（详情页按钮） */
    val detailOps: List<String> = listOf(
        "挂起", "隐藏", "阻止卸载", "禁用用户控制", "禁用计量数据",
        "清除数据", "卸载", "权限管理", "托管配置", "系统详情",
    )
}

// ───────────────────────── 四、密码与锁屏 ─────────────────────────

object YuhengPasswordCatalog {

    val groups: List<FeatureGroup> = listOf(
        FeatureGroup(
            "密码策略",
            listOf(
                FeatureEntry("password_quality", "必需密码质量", "Android 11 以下的密码强度要求", kind = EntryKind.ENUM, minApi = 21),
                FeatureEntry("password_complexity", "必需密码复杂度", "Android 11 及以上的密码强度要求", kind = EntryKind.ENUM, minApi = 31),
                FeatureEntry("password_expiration", "密码有效期", "到期后强制修改密码", kind = EntryKind.TEXT, minApi = 21),
                FeatureEntry("password_history", "密码历史长度", "禁止重复使用最近若干次密码", kind = EntryKind.TEXT, minApi = 21),
                FeatureEntry("max_failed_passwords", "最大失败次数", "超过后自动擦除设备数据", kind = EntryKind.TEXT, minApi = 21),
            ),
        ),
        FeatureGroup(
            "锁屏与超时",
            listOf(
                FeatureEntry("keyguard_features", "禁用锁屏特性", "禁用相机、通知、指纹等锁屏功能", kind = EntryKind.SCREEN, minApi = 21),
                FeatureEntry("lock_timeout", "自动锁屏超时", "无操作后自动锁屏的时长", kind = EntryKind.TEXT, minApi = 21),
                FeatureEntry("strong_auth_timeout", "强认证超时", "锁定后需强认证的时长", kind = EntryKind.TEXT, minApi = 26),
            ),
        ),
        FeatureGroup(
            "重置密码",
            listOf(
                FeatureEntry("reset_token", "重置密码令牌", "生成 / 清除用于重置密码的令牌", kind = EntryKind.SCREEN, minApi = 26),
                FeatureEntry("reset_password", "重置密码", "用令牌直接重置设备密码", kind = EntryKind.TEXT, minApi = 21, danger = true),
            ),
        ),
    )
}

// ───────────────────────── 五、网络 ─────────────────────────

object YuhengNetworkCatalog {

    val groups: List<FeatureGroup> = listOf(
        FeatureGroup(
            "Wi-Fi",
            listOf(
                FeatureEntry("wifi_overview", "Wi-Fi 总览", "开关、连接状态、MAC 地址", kind = EntryKind.SCREEN),
                FeatureEntry("wifi_networks", "已保存网络", "查看 / 编辑 / 启用 / 删除已保存网络", kind = EntryKind.SCREEN),
                FeatureEntry("wifi_min_security", "最小 Wi-Fi 安全级别", "低于该级别的网络不可连接", kind = EntryKind.ENUM, minApi = 33, deviceOnly = true),
                FeatureEntry("wifi_ssid_policy", "SSID 策略", "白名单 / 黑名单限定可用 SSID", kind = EntryKind.SCREEN, minApi = 33, deviceOnly = true),
                FeatureEntry("wifi_lockdown", "锁定网络配置", "禁止修改管理员配置的网络", kind = EntryKind.TOGGLE, minApi = 30, deviceOnly = true),
            ),
        ),
        FeatureGroup(
            "连接策略",
            listOf(
                FeatureEntry("private_dns", "私有 DNS", "自动 / 关闭 / 指定主机名", kind = EntryKind.ENUM, minApi = 29, deviceOnly = true),
                FeatureEntry("always_on_vpn", "始终开启 VPN", "指定 VPN 应用并锁定", kind = EntryKind.SCREEN, minApi = 24, deviceOnly = true),
                FeatureEntry("global_proxy", "推荐全局代理", "为设备设定推荐的代理设置", kind = EntryKind.SCREEN, minApi = 21, deviceOnly = true),
                FeatureEntry("preferential_network", "首选网络服务", "指定优先使用的网络", kind = EntryKind.SCREEN, minApi = 31),
                FeatureEntry("override_apn", "覆盖 APN", "自定义运营商接入点", kind = EntryKind.SCREEN, minApi = 28, deviceOnly = true),
            ),
        ),
        FeatureGroup(
            "流量与日志",
            listOf(
                FeatureEntry("network_stats", "网络统计", "按用户 / 应用查询流量明细", kind = EntryKind.SCREEN, minApi = 23),
                FeatureEntry("network_log", "网络日志", "记录 DNS 与连接事件并导出", kind = EntryKind.SCREEN, minApi = 26, deviceOnly = true),
            ),
        ),
    )
}

// ───────────────────────── 六、用户管理 ─────────────────────────

object YuhengUserCatalog {

    val actions: List<FeatureEntry> = listOf(
        FeatureEntry("user_create", "创建用户", "新建受管理的用户", kind = EntryKind.SCREEN, minApi = 24, deviceOnly = true),
        FeatureEntry("user_list", "用户列表", "启动 / 切换 / 停止 / 删除用户", kind = EntryKind.SCREEN, minApi = 21, deviceOnly = true),
        FeatureEntry("user_info", "用户信息", "当前用户的属性与状态", kind = EntryKind.SCREEN),
        FeatureEntry("user_affiliation", "附属 ID", "设定用户的组织附属标识", kind = EntryKind.TEXT, minApi = 26),
        FeatureEntry("logout_enabled", "允许注销", "允许用户从会话中注销", kind = EntryKind.TOGGLE, minApi = 28),
        FeatureEntry("session_message", "会话提示信息", "登录 / 注销时显示的提示", kind = EntryKind.TEXT, minApi = 28),
    )

    val workProfile: List<FeatureEntry> = listOf(
        FeatureEntry("wp_create", "创建工作资料", "通过系统引导创建托管工作资料", kind = EntryKind.ACTION, minApi = 21, deviceOnly = true),
        FeatureEntry("wp_suspend_personal", "挂起个人应用", "工作时段内挂起个人资料的应用", kind = EntryKind.TOGGLE, minApi = 30),
        FeatureEntry("wp_max_time_off", "最长暂停时长", "个人应用可挂起的最长时间", kind = EntryKind.TEXT, minApi = 30),
        FeatureEntry("wp_cross_filters", "跨资料 Intent 过滤器", "配置个人与工作资料间可互通的 Intent", kind = EntryKind.SCREEN, minApi = 21),
        FeatureEntry("wp_wipe", "删除工作资料", "擦除工作资料及其全部数据", kind = EntryKind.ACTION, minApi = 21, danger = true),
    )
}

/** 通用工具：判断条目在当前系统上是否可用 */
fun FeatureEntry.availableOn(): Boolean = Build.VERSION.SDK_INT >= minApi
