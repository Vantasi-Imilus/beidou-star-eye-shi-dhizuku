// Shizuku 特权服务（原版源码，MANAGER_APPLICATION_ID 已改为本应用包名）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.refine)
}

android {
    namespace = "moe.shizuku.server"
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
    implementation(libs.gson)
    api(libs.parcelablelist)

    api(libs.rikka.shizuku.aidl)
    implementation(project(":shizuku:common"))
    implementation(project(":shizuku:shared"))
    compileOnly(project(":shizuku:provider"))
    implementation(project(":shizuku:starter"))
    implementation(project(":shizuku:rish"))
    implementation(project(":shizuku:server-shared"))

    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)
}
