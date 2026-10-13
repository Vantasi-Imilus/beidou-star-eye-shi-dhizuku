// 核心模型模块：Feature 抽象与北斗七星枚举
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
