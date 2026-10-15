// 天权中枢 —— Termux-X
// 许可证：原项目及 terminal-emulator / terminal-view 分发源码均为 GPL-3.0 → 本模块零拷贝自研，
// 仅仿照其「终端模拟器」功能；伪终端由 app 模块的 libpty.so（自研 C）提供。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.feature.tianquan"
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

    // 天枢（Shizuku 客户端）与开阳（Dhizuku API，MIT）—— 终端可经其取得 ADB / 设备所有者权限
    implementation(project(":shizuku:api"))
    implementation(project(":core:shell"))
    implementation(libs.dhizuku.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // 浮动窗：ComposeView 挂到 overlay 窗口需手工注入 ViewTree LifecycleOwner / SavedStateRegistryOwner
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.savedstate)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
