// 宿主应用模块 —— 北斗·群星之眼
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.beidou.star.eye.furry.vantasiimilus.god.api"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.beidou.star.eye.furry.vantasiimilus.god.api"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
            }
        }
    }

    buildTypes {
        release {
            // 不开 R8 / 资源压缩：与 debug 行为完全等价
            // （工程大量依赖反射与 Gson 字段名，混淆需额外 keep 规则，暂不启用）
            isMinifyEnabled = false
            isShrinkResources = false
            // 复用 debug 签名：可直接覆盖安装已装的 debug 版
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    // 原生 starter(libshizuku.so) 需以独立可执行文件运行，故解压 .so 到文件系统
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:tianshu"))
    implementation(project(":feature:tianxuan"))
    implementation(project(":feature:tianji"))
    implementation(project(":feature:tianquan"))
    implementation(project(":feature:yuheng"))
    implementation(project(":feature:kaiyang"))
    implementation(project(":feature:yaoguang"))
    implementation(project(":feature:fanxiang"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)

    implementation(libs.libcxx)
    implementation(libs.boringssl)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    debugImplementation(libs.compose.ui.tooling)
}
