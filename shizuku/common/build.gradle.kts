// Shizuku common 工具类（原版源码）
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "moe.shizuku.common"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 提供 android.os.SELinux 等隐藏 API 桩
    compileOnly(libs.hidden.stub)
}
