plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Гра — один файл ../pelmen-game/pelmen.html. Перед збіркою він копіюється в assets/index.html
// (це робить workflow .github/workflows/pelmen-apk.yml).
val gameHtml = rootProject.file("../pelmen-game/pelmen.html")
val assetHtml = file("src/main/assets/index.html")
if (gameHtml.exists() && (!assetHtml.exists() || assetHtml.lastModified() < gameHtml.lastModified())) {
    assetHtml.parentFile.mkdirs()
    gameHtml.copyTo(assetHtml, overwrite = true)
}

android {
    namespace = "com.installpixel.pelmen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.installpixel.pelmen"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("pelmenVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("pelmenVersionName") as String?) ?: "1.0"
    }

    signingConfigs {
        create("release") {
            // Ключ підпису гри. Однаковий для всіх версій — тому оновлення ставляться поверх старої
            // версії і прогрес не губиться.
            storeFile = rootProject.file("keystore/pelmen-release.jks")
            storePassword = System.getenv("PELMEN_STORE_PASSWORD") ?: "pelmen-kitchen-2026"
            keyAlias = "pelmen"
            keyPassword = System.getenv("PELMEN_KEY_PASSWORD") ?: "pelmen-kitchen-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
}
