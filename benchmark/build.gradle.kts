/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

plugins {
    alias(libs.plugins.agp.test)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project

android {
    namespace = "com.drs.smartkeyboard.benchmark"
    compileSdk = projectCompileSdk.toInt()

    defaultConfig {
        minSdk = projectMinSdk.toInt().coerceAtLeast(24)
        targetSdk = projectTargetSdk.toInt()
        testInstrumentationRunner = "androidx.benchmark.macro.junit4.MacrobenchmarkRunner"
    }

    buildTypes {
        // The macrobenchmark variant: debuggable (profiler access) but
        // release-like (matching the app's release build for honest numbers).
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // The module instruments the app APK — this IS a test module.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext)
}
