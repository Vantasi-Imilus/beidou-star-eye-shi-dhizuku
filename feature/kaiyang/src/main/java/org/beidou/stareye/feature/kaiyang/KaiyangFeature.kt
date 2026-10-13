package org.beidou.stareye.feature.kaiyang

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 开阳中枢 —— Dhizuku
 *
 * 已集成：Device Owner 管理 + MIT 转接层 + 双通道激活 + 身份转移/解除。
 */
val KaiyangFeature = Feature(
    id = "kaiyang",
    star = BeidouStar.KAIYANG,
    title = "开阳中枢",
    subtitle = "Dhizuku · 设备所有者授权",
    description = "基于 Device Owner 的权限授权服务，无需 Root 即可为应用授予系统权限。",
    sourceProject = "Dhizuku",
    status = FeatureStatus.AVAILABLE,
)
