// 共享核心 —— 应用冻结状态的统一判定与变更事件
// 天璇 / 玉衡 / 瑶光共用同一判定源，保证同一应用在不同中枢页面显示一致。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api.core.freeze"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
