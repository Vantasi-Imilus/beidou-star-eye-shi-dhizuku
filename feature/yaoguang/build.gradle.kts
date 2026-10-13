// 瑶光中枢 —— 雹（Hail）
// 许可证：原项目 Hail 为 GPL-3.0 → 本模块零拷贝自研，
// 仅仿照其「应用冻结 / 隐藏」功能面，用 Android 原生 API + 天枢 / 开阳特权通道实现。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.feature.yaoguang"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    // 冻结状态统一判定（与天璇 / 玉衡同源，保证跨中枢一致）
    implementation(project(":core:freeze"))
    // 天枢（Shizuku）shell 通道：强停 / 禁用 / 隐藏 / 挂起
    implementation(project(":core:shell"))
    implementation(project(":shizuku:api"))
    // 复用开阳中枢的 Device Owner admin 组件（隐藏 / 挂起语义走 DPM）
    implementation(project(":feature:kaiyang"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.gson)
    implementation(libs.tinypinyin)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
