import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.kotlin.serialization)
}

val projectMinSdk: String by project
val projectCompileSdk: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Xwhen-guards",
        ))
    }
}

configure<LibraryExtension> {
    namespace = "org.drs.lib.android"
    compileSdk = projectCompileSdk.toInt()

    defaultConfig {
        minSdk = projectMinSdk.toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("beta") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // FIXME: This is a workaround! Otherwise :lib:snygg:generateJsonSchema breakes.
    //  Remove the lint block when we've migrated to the newDsl.
    lint {
        disable.addAll(
            listOf(
                "UElementAsPsi",
                "ApplySharedPref",
                "CommitTransaction",
                "Recycle",
                "CommitPrefEdits",
            )
        )
    }
}

dependencies {
    implementation(projects.lib.kotlin)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}
