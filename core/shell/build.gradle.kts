// 共享核心 —— Shizuku shell 执行原语
// 各中枢（天璇/天玑/…）以 shell / root 身份执行系统命令的统一通道。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.core.shell"
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
    // 天枢中枢（Shizuku 客户端 API）
    api(project(":shizuku:api"))
}
