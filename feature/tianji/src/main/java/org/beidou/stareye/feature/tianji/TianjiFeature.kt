package org.beidou.stareye.feature.tianji

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 天玑中枢 —— InstallerX
 *
 * 方案A（核心能力子集，自研实现）：APK/APKS/XAPK 解析、经天枢静默安装、
 * 系统安装器回退、卸载、安装记录，以及安装/卸载 intent 转接层。
 * 原项目为 GPL-3.0，本模块代码零拷贝。
 */
val TianjiFeature = Feature(
    id = "tianji",
    star = BeidouStar.TIANJI,
    title = "天玑中枢",
    subtitle = "InstallerX · 现代化安装器",
    description = "现代化 APK 安装器：解析安装包信息、经天枢中枢静默安装、卸载应用，并作为系统安装器接管第三方安装请求。",
    sourceProject = "InstallerX",
    status = FeatureStatus.AVAILABLE,
)
