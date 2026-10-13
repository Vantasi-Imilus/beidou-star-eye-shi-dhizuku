package org.beidou.stareye.core.model

/**
 * 功能集成状态。
 */
enum class FeatureStatus {
    /** 功能已可用 */
    AVAILABLE,

    /** 正在集成 / 即将上线 */
    COMING_SOON,
}

/**
 * 一个功能中枢的元数据抽象。
 *
 * 每个北斗中枢由一个独立的 feature 模块实现，模块对外暴露一个 [Feature] 实例，
 * 宿主应用通过 [org.beidou.stareye.feature.FeatureRegistry] 汇总渲染。
 * 这一抽象保证了工程的可拓展性：新增功能只需新增模块并注册。
 */
data class Feature(
    /** 唯一标识，与模块路径对应，如 "tianshu" */
    val id: String,
    /** 对应的北斗星位 */
    val star: BeidouStar,
    /** 中枢名称，如「天枢中枢」 */
    val title: String,
    /** 副标题，如「Shizuku · 系统权限授权」 */
    val subtitle: String,
    /** 功能描述 */
    val description: String,
    /** 集成的开源项目名 */
    val sourceProject: String,
    /** 当前状态 */
    val status: FeatureStatus = FeatureStatus.COMING_SOON,
) {
    val chineseStarName: String get() = star.chineseName
}
