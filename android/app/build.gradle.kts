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
        versionCode = 3
        versionName = "0.1.2"
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

// 关键：每次构建前把仓库根目录的 index.html 拷进 assets，
// 这样网页只需要维护一份，APK 里永远是同一份代码
val syncWeb = tasks.register<Copy>("syncWebAssets") {
    from(rootProject.projectDir.parentFile) {
        include("index.html")
        include("test-tank.png")
        include("v1-lbm.html")
    }
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") { dependsOn(syncWeb) }