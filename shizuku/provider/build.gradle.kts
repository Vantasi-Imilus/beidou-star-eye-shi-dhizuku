// Shizuku 授权桥接 Provider（原版源码）
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.provider"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
