// 共享核心 —— 应用冻结状态的统一判定、变更事件与解冻执行
// 天璇 / 玉衡 / 瑶光共用同一判定源，保证同一应用在不同中枢页面显示一致；
// 解冻执行（按实际形态逐一清除）供各中枢复用（如天玑安装完成后自动解冻）。
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

dependencies {
    // 解冻执行需经天枢 shell（设备所有者可用时优先 DPM，无需 shell）
    implementation(project(":core:shell"))
}
