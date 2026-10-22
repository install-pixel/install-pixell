plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The app provides ONNX Runtime for Android (same ai.onnxruntime API); tests use the desktop build.
val onnxRuntimeVersion = "1.20.0"

dependencies {
    implementation(project(":core"))
    compileOnly("com.microsoft.onnxruntime:onnxruntime:$onnxRuntimeVersion")

    testImplementation("com.microsoft.onnxruntime:onnxruntime:$onnxRuntimeVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.test {
    // Directory with unpacked model packages (see tools/mt). Tests that need real models are skipped without it.
    System.getenv("MT_MODELS_DIR")?.let { systemProperty("mt.modelsDir", it) }
    System.getenv("MT_REPORT_DIR")?.let { systemProperty("mt.reportDir", it) }
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
