// Shizuku server 共享代码（原版源码）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.refine)
}

android {
    namespace = "rikka.shizuku.server"
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
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.parcelablelist)

    api(libs.rikka.shizuku.aidl)
    api(project(":shizuku:shared"))
    api(project(":shizuku:rish"))

    implementation(libs.refine.runtime)
    implementation(libs.refine.annotation)
    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)
}
