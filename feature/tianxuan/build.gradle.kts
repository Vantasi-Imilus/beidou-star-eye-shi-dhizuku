// 天璇中枢 —— App Manager
// 许可证：原项目 App Manager 为 GPL-3.0 → 本模块零拷贝自研，
// 仅仿照其「应用管理」功能，用 Android 原生 API + 天枢(Shizuku)/开阳(Device Owner)权限实现。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.feature.tianxuan"
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

    // 天枢中枢（Shizuku 客户端）：以 shell / root 身份执行系统级应用管理命令
    implementation(project(":shizuku:api"))
    implementation(project(":core:shell"))
    // 冻结状态统一判定（与玉衡 / 瑶光跨中枢一致）
    implementation(project(":core:freeze"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.gson)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    // APK 编辑重签名（v1+v2+v3）。apksig 为 Apache-2.0（AGP 自带工具库），许可允许链接；
    // 本模块唯一新增依赖。若 Android 运行时不可用，代码已带未签名产物兜底。
    implementation("com.android.tools.build:apksig:8.7.3")
}
