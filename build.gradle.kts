// 北斗·群星之眼 —— 根构建脚本
// 仅声明插件版本，不在此应用；各模块按需应用。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.refine) apply false
}
