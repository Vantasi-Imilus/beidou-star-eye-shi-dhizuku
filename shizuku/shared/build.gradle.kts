// Shizuku 共享常量（原版源码）
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.shared"
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
    implementation(libs.androidx.annotation)
}
