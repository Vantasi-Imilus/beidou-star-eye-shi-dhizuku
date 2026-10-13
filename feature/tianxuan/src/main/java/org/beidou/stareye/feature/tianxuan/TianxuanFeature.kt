package org.beidou.stareye.feature.tianxuan

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 天璇中枢 —— App Manager
 *
 * 方案A（核心能力子集，自研实现）：应用列表/详情、冻结解冻、强停、清数据、卸载、导出 APK、操作记录。
 * 原项目为 GPL-3.0，本模块代码零拷贝，用 Android 原生 API + 天枢/开阳权限实现。
 */
val TianxuanFeature = Feature(
    id = "tianxuan",
    star = BeidouStar.TIANXUAN,
    title = "天璇中枢",
    subtitle = "App Manager · 应用与组件管理",
    description = "应用管理器：查看应用列表与详情，冻结/解冻、强停、清数据、卸载、导出 APK，并记录全部操作。",
    sourceProject = "App Manager",
    status = FeatureStatus.AVAILABLE,
)
