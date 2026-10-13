package org.beidou.stareye.feature.tianquan

import org.beidou.stareye.core.model.BeidouStar
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.core.model.FeatureStatus

/**
 * 天权中枢 —— Termux-X
 *
 * 方案A（核心能力子集，自研实现）：自研终端模拟器（C0/CSI/SGR/滚动区域/宽字符）
 * + 原生 pty 会话（libpty.so），支持彩色输出、光标控制、回滚历史与附加键排。
 * 原项目为 GPL-3.0，本模块代码零拷贝。
 */
val TianquanFeature = Feature(
    id = "tianquan",
    star = BeidouStar.TIANQUAN,
    title = "天权中枢",
    subtitle = "Termux-X · 终端模拟器",
    description = "终端模拟器：原生伪终端会话，支持彩色输出、光标控制、回滚历史与附加键排；可选配色与字号。",
    sourceProject = "Termux-X",
    status = FeatureStatus.AVAILABLE,
)
