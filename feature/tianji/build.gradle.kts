// 天玑中枢 —— InstallerX
// 许可证：原项目 InstallerX-Revived 为 GPL-3.0 → 本模块零拷贝自研，
// 仅仿照其「现代化安装器」功能，用 Android 原生 API + 天枢(Shizuku)权限实现。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.feature.tianji"
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

    // 天枢中枢（Shizuku 客户端）：以 shell / root 身份执行 pm install
    implementation(project(":shizuku:api"))
    implementation(project(":core:shell"))
    // 开阳中枢（Dhizuku API，MIT）：以设备所有者身份执行特权操作
    implementation(libs.dhizuku.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.gson)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
