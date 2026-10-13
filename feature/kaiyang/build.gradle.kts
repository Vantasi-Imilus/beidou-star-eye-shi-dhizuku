// 开阳中枢 —— Dhizuku（Device Owner 授权）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.feature.kaiyang"
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

    // Dhizuku 依赖 Shizuku 做守护进程引导 → 用我们的天枢
    implementation(project(":shizuku:api"))
    implementation(project(":shizuku:provider"))

    implementation(libs.dhizuku.api)
    implementation(libs.dhizuku.server.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.gson)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
