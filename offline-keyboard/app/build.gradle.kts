plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Shortcut presets ship inside the APK from the same folder that is published on GitHub.
    sourceSets["main"].assets.srcDir("../shortcut-presets")
    // The example translation glossary, the same file as offline-keyboard/glossary/ on GitHub.
    sourceSets["main"].assets.srcDir("../glossary")

    namespace = "com.offlinekeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offlinekeyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"

        // Native code of Vosk and ONNX Runtime for phones only (64- and 32-bit ARM).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            // The emulator UI tests run on x86_64 (-PemulatorAbi=x86_64); phones never get this build.
            (project.findProperty("emulatorAbi") as String?)?.let { abiFilters += it }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One fixed key for every build, so a new APK installs over the previous one (CI runners would
    // otherwise sign each build with a fresh random debug key, and Android refuses such updates).
    // The key only identifies this sideloaded app; it is not a Play Store key.
    signingConfigs {
        create("shared") {
            storeFile = file("signing/offline-keyboard.jks")
            storePassword = "offlinekeyboard"
            keyAlias = "offlinekeyboard"
            keyPassword = "offlinekeyboard"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
            isShrinkResources = false
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
        compose = true
        buildConfig = true
    }

    // Store native libraries compressed: a much smaller APK to download (they are unpacked once on install).
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mt"))

    // Offline speech recognition (Apache 2.0) and the JNA runtime it uses.
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    // Offline translation runtime (MIT); same ai.onnxruntime API the :mt module compiles against.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
