package org.beidou.stareye.feature.tianshu

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 天枢中枢 —— Shizuku
 *
 * 已集成：原版服务端嵌入 + 客户端 API + 原生 starter + 应用内无线激活 + 授权全链路。
 */
val TianshuFeature = Feature(
    id = "tianshu",
    star = BeidouStar.TIANSHU,
    title = "天枢中枢",
    subtitle = "Shizuku · 系统权限授权",
    description = "以 ADB 或 Root 启动 Shizuku 服务，为应用安全授予系统级 API 权限，是诸多高级功能的基石。",
    sourceProject = "Shizuku",
    status = FeatureStatus.AVAILABLE,
)
