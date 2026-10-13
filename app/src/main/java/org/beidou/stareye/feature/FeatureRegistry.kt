package org.beidou.stareye.feature

import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.feature.kaiyang.KaiyangFeature
import org.beidou.stareye.feature.tianji.TianjiFeature
import org.beidou.stareye.feature.tianquan.TianquanFeature
import org.beidou.stareye.feature.tianshu.TianshuFeature
import org.beidou.stareye.feature.tianxuan.TianxuanFeature
import org.beidou.stareye.feature.yaoguang.YaoguangFeature
import org.beidou.stareye.feature.yuheng.YuhengFeature

/**
 * 功能中枢注册表：汇总全部北斗中枢。
 *
 * 每个中枢由独立的 feature 模块实现并对外暴露一个 [Feature] 实例；
 * 宿主应用在此统一注册、按斗序排列并渲染。新增功能中枢只需：
 *  1. 新建一个 feature 模块，暴露 Feature 实例；
 *  2. 在 settings.gradle.kts 中 include 该模块；
 *  3. 在 app 的依赖与 [FeatureRegistry.all] 中追加。
 */
object FeatureRegistry {
    val all: List<Feature> = listOf(
        TianshuFeature,
        TianxuanFeature,
        TianjiFeature,
        TianquanFeature,
        YuhengFeature,
        KaiyangFeature,
        YaoguangFeature,
    ).sortedBy { it.star.order }
}
