// Shizuku RISH 交互 shell（原版 Java 源码；native 部分暂缓，后续补 NDK）
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.rish"
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
    implementation(project(":shizuku:api"))
    implementation(libs.androidx.annotation)
}
