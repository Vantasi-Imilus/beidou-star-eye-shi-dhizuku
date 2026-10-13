// Shizuku 客户端 API（原版源码，含 Sui）
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.api"
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
    api(libs.rikka.shizuku.aidl)
    api(project(":shizuku:shared"))
    implementation(libs.androidx.annotation)
}
