// Shizuku 服务启动器（原版源码，身份已改为本应用包名）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.refine)
}

android {
    namespace = "rikka.shizuku.starter"
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
    implementation(project(":shizuku:common"))
    implementation(project(":shizuku:shared"))
    implementation(project(":shizuku:server-shared"))
    compileOnly(project(":shizuku:provider"))

    implementation(libs.androidx.annotation)
    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)
    implementation(libs.refine.runtime)
    implementation(libs.refine.annotation)
}
