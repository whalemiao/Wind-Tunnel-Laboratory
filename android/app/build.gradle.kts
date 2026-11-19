plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.whalemiao.windtunnel"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.whalemiao.windtunnel"
        minSdk = 24
        targetSdk = 34
        versionCode = 36
        versionName = "0.3.24"
    }

    // debug 版用自动生成的调试密钥签名，能直接装（不需要自己配 keystore）
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 关键：每次构建前把仓库根目录的网页拷进 assets，
// 这样网页只维护一份，APK 里的永远是最新版
// index.html 负责选引擎（GPU 优先，不行退回 CPU 版），三个文件都要带上
val syncWeb = tasks.register<Copy>("syncWebAssets") {
    from(rootProject.projectDir.parentFile) {
        include("index.html")
        include("cpu.html")
        include("gpu/*.html")
        include("test-tank.png")
        include("v1-lbm.html")
        include("presets/*.png")
    }
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") { dependsOn(syncWeb) }
