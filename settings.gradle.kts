pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BeidouStarEye"

// 宿主应用
include(":app")

// 共享核心模型（Feature 抽象、北斗七星枚举）
include(":core:model")
// 共享 UI 原语（星空主题、液态玻璃组件、星空背景）
include(":core:ui")
// 共享 Shizuku shell 执行原语（天璇/天玑等中枢复用）
include(":core:shell")
// 共享应用冻结状态判定与变更事件（天璇/玉衡/瑶光共用，保证跨中枢状态一致）
include(":core:freeze")

// Shizuku 原版源码模块（客户端；aidl 接口用预编译 Maven 版 dev.rikka.shizuku:aidl）
include(":shizuku:shared")
include(":shizuku:api")
include(":shizuku:provider")

// Shizuku 服务端源码模块（server / starter / common / server-shared / rish）
include(":shizuku:common")
include(":shizuku:server-shared")
include(":shizuku:starter")
include(":shizuku:server")
include(":shizuku:rish")

// 七大北斗中枢功能模块（每个对应一个开源项目，后续逐个深度迁入）
include(":feature:tianshu")    // Shizuku  · 天枢中枢
include(":feature:tianxuan")   // AppManager · 天璇中枢
include(":feature:tianji")     // InstallerX · 天玑中枢
include(":feature:tianquan")   // Termux-X   · 天权中枢
include(":feature:yuheng")     // OwnDroid   · 玉衡中枢
include(":feature:kaiyang")    // Dhizuku    · 开阳中枢
include(":feature:yaoguang")   // 雹 Hail    · 瑶光中枢
