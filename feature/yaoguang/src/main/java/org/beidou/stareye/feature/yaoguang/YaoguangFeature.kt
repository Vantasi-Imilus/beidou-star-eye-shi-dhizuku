package org.beidou.stareye.feature.yaoguang

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 瑶光中枢 —— 雹（Hail）
 *
 * 已集成（自研实现，零拷贝）：应用冻结 / 隐藏全功能面 ——
 * 四种冻结语义（强停 / 禁用 / 隐藏 / 挂起）× 多特权通道（开阳 Device Owner / 天枢 Shizuku / Root），
 * 我的清单（标签 / 置顶 / 白名单）、自动冻结、快捷方式与磁贴、对外 API。
 * 冻结状态经 core:freeze 统一判定，与天璇 / 玉衡跨中枢一致。
 */
val YaoguangFeature = Feature(
    id = "yaoguang",
    star = BeidouStar.YAOGUANG,
    title = "瑶光中枢",
    subtitle = "雹 Hail · 应用冻结与隐藏",
    description = "以「我的清单」为中心管理应用使用态：强停 / 禁用 / 隐藏 / 挂起四种冻结语义，配合自动冻结、快捷方式与磁贴，让不常用的应用静默休眠。",
    sourceProject = "Hail",
    status = FeatureStatus.AVAILABLE,
)
