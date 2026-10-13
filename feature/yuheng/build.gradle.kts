// 玉衡中枢 —— OwnDroid
// 许可证：原项目 OwnDroid 为 GPL-3.0 → 本模块零拷贝自研，
// 仅仿照其「设备所有者深度管控」功能，用 Android 原生 DevicePolicyManager / UserManager 等 API 实现。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.feature.yuheng"
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
    // 冻结状态变更事件（与天璇 / 瑶光跨中枢同步）
    implementation(project(":core:freeze"))
    // 与开阳中枢共用同一个 Device Owner admin 组件（同一应用，不冲突）
    implementation(project(":feature:kaiyang"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.gson)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
