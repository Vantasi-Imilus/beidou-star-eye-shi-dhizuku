package org.beidou.stareye.feature.yuheng

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 玉衡中枢 —— OwnDroid
 *
 * 已集成（自研实现，零拷贝）：设备所有者深度管控全功能面 ——
 * 系统策略、应用管理、用户管理、69 项用户限制、密码与锁屏、网络策略，
 * 以及 24 个对外广播 API。与开阳中枢共用同一 Device Owner admin 组件。
 */
val YuhengFeature = Feature(
    id = "yuheng",
    star = BeidouStar.YUHENG,
    title = "玉衡中枢",
    subtitle = "OwnDroid · 设备所有者管理",
    description = "基于 Device Owner 的深度设备管控：系统策略、应用与用户管理、69 项用户限制、密码与锁屏、网络策略，并提供对外 API。",
    sourceProject = "OwnDroid",
    status = FeatureStatus.AVAILABLE,
)
