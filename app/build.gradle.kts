/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

import com.android.build.api.dsl.ApplicationExtension
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.kotlinx.kover)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project
val projectVersionNameSuffix = projectVersionName.substringAfter("-", "").let { suffix ->
    if (suffix.isNotEmpty()) {
        "-$suffix"
    } else {
        suffix
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-jvm-default=enable",
            "-Xwhen-guards",
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
            "-XXLanguage:+LocalTypeAliases",
        ))
    }
}

configure<ApplicationExtension> {
    namespace = "com.drs.smartkeyboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    ndkVersion = tools.versions.ndk.get()

    signingConfigs {
        create("release") {
            // The release keystore is provisioned OUTSIDE version control:
            // CI decodes it from the DRS_KEYSTORE_B64 secret into the path
            // given by DRS_KEYSTORE_PATH (see .github/workflows/release.yml).
            // Local builds fall back to the (git-ignored) local keystore file
            // so the same signing key is used everywhere for update continuity.
            storeFile = System.getenv("DRS_KEYSTORE_PATH")?.let { rootProject.file(it) }
                ?: rootProject.file("keystore/DRS-Release-Key.keystore")
            storePassword = System.getenv("DRS_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("DRS_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("DRS_KEY_PASSWORD") ?: "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        applicationId = "com.drs.smartkeyboard"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        buildConfigField("String", "DRS_ADDONS_API_VERSION", "\"v~draft2\"")
        buildConfigField("String", "DRS_ADDONS_URL", "\"github.com/CTO-DRS/DRS-SmartKeyboard\"")
        buildConfigField("String", "DRS_RELEASES_API", "\"https://api.github.com/repos/CTO-DRS/DRS-SmartKeyboard/releases/latest\"")
        buildConfigField("String", "DRS_PACKAGES_MANIFEST_URL", "\"https://raw.githubusercontent.com/CTO-DRS/DRS-SmartKeyboard/main/packages/manifest.json\"")

        sourceSets {
            maybeCreate("main").apply {
                assets.directories += "src/main/assets"
            }
        }
    }

    bundle {
        language {
            // We disable language split because DRS Keyboard does not use
            // runtime Google Play Service APIs and thus cannot dynamically
            // request to download the language resources for a specific locale.
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug+${getGitCommitHash(short = true).get()}"

            isDebuggable = true
            isJniDebuggable = false
        }

        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = projectVersionNameSuffix

            // DRS v1.18.0: R8 re-enabled. Root cause investigation of the
            // v1.7-era "blank screen" release (3.1MB dex without any UI):
            // with the current AGP 9.0.0 toolchain the failure does NOT
            // reproduce — a controlled experiment (minify run with and
            // without explicit entry-point keeps) produced a complete dex
            // in both cases (6.8k+ classes, every Compose screen, material3,
            // the JetPref runtime and the KSP-generated preference models
            // all present), so the platform pipeline has since been fixed
            // upstream. The explicit entry-point keeps below stay as
            // belt-and-suspenders, and the validate*R8Dex tasks gate the
            // build: if a future toolchain update ever prunes the UI graph
            // again, the build fails loudly instead of shipping a blank
            // screen.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true
        }

        named("release") {
            versionNameSuffix = projectVersionNameSuffix

            signingConfig = signingConfigs.getByName("release")
            // DRS v1.18.0: see the beta block comment — R8 is re-enabled
            // with the dex-completeness validation gate.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true
        }

        create("benchmark") {
            initWith(getByName("release"))

            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench+${getGitCommitHash(short = true).get()}"

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    lint {
        baseline = file("lint.xml")
        // Workaround for a known AGP lint tool crash (not a code defect):
        // the UElementAsPsi detector throws while resolving KtLambdaExpression
        // in Flog.kt under the K2 frontend. Lint itself suggests disabling it.
        disable += "UElementAsPsi"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

tasks.withType<Test> {
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
    }
    useJUnitPlatform()
}

kover {
    useJacoco()

    // DRS M0.5 — the coverage floor gates the DRS CORE line: the drs,
    // drs.ai and drs.privacy packages (the engines, not the surface).
    // The drs.ui package is excluded on purpose: pure UI-composable code
    // (6247 lines at 0% in the JVM harness) would drag the honest engine
    // coverage down without being measurable here. The floor is 60% LINE
    // coverage, aggregated as covered percentage; verified per variant
    // (:app:koverVerifyDebug in CI gates).
    reports {
        filters {
            includes {
                packages(
                    "com.drs.smartkeyboard.drs",
                    "com.drs.smartkeyboard.drs.ai",
                    "com.drs.smartkeyboard.drs.privacy",
                )
            }
            excludes {
                packages("com.drs.smartkeyboard.drs.ui")
            }
        }
        verify {
            rule("DRS core line coverage floor (M0.5)") {
                minBound(60, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
            }
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // testImplementation(composeBom)
    // androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.window.core)
    implementation(libs.cache4k)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.lib.jetpref.datastoreModel)
    ksp(projects.lib.jetpref.datastoreModelProcessor)
    implementation(projects.lib.jetpref.datastoreUi)
    implementation(projects.lib.jetpref.materialUi)

    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.compose)
    implementation(projects.lib.kotlin)
    implementation(projects.lib.snygg)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// DRS v1.18.0: R8 dex-completeness gate. The v1.7-era "blank screen"
// release shipped a minified dex from which every Compose screen had
// been pruned — a failure that is invisible at build time and only
// shows up as an empty UI on the device. These tasks scan the R8 output
// dex(es) for a fixed set of marker class descriptors (the four manifest
// components, the reflectively-loaded JetPref preference model impl and
// the typing settings screen) and fail the build when any of them is
// missing, so a toolchain regression can never reach a release silently.
// ASCII descriptors are stored verbatim (MUTF-8) in the dex string pool,
// so a raw byte scan is exact and needs no external tooling.
listOf("beta", "release").forEach { variantName ->
    val variantCap = variantName.replaceFirstChar { it.uppercaseChar() }
    val minifyTask = "minify${variantCap}WithR8"
    val validateTask = "validate${variantCap}R8Dex"
    tasks.register(validateTask) {
        group = "verification"
        description = "Fails unless the minified $variantName dex still contains the DRS marker classes."
        dependsOn(minifyTask)
        inputs.dir(
            layout.buildDirectory.dir("intermediates/dex/$variantName/$minifyTask"),
        )
        outputs.upToDateWhen { false }
        doLast {
            val dexDir = layout.buildDirectory.dir("intermediates/dex/$variantName/$minifyTask").get().asFile
            val dexFiles = dexDir.walkTopDown().filter { it.isFile && it.extension == "dex" }.toList()
            if (dexFiles.isEmpty()) {
                throw GradleException("R8 dex validation: no dex output found under ${dexDir.path}")
            }
            val markers = listOf(
                "Lcom/drs/smartkeyboard/DrsApplication;",
                "Lcom/drs/smartkeyboard/DrsImeService;",
                "Lcom/drs/smartkeyboard/DrsSpellCheckerService;",
                "Lcom/drs/smartkeyboard/app/DrsAppActivity;",
                "Lcom/drs/smartkeyboard/app/DrsPreferenceModelImpl;",
                "Lcom/drs/smartkeyboard/app/settings/typing/TypingScreenKt;",
            )
            val missing = markers.filter { marker ->
                val needle = marker.toByteArray(Charsets.US_ASCII)
                dexFiles.none { f -> indexOf(f.readBytes(), needle) >= 0 }
            }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "R8 dex validation FAILED for $variantName: marker classes missing from the " +
                        "minified dex (the v1.7 blank-screen signature): $missing",
                )
            }
        }
    }
    tasks.matching { it.name == "assemble$variantCap" }.configureEach {
        dependsOn(validateTask)
    }
}

/** Plain byte-search (works on raw dex bytes, no external tooling). */
fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    if (haystack.size < needle.size) return -1
    outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!File(".git").exists()) {
        return providers.provider { "null" }
    }

    val execProvider = providers.exec {
        if (short) {
            commandLine("git", "rev-parse", "--short", "HEAD")
        } else {
            commandLine("git", "rev-parse", "HEAD")
        }
    }
    return execProvider.standardOutput.asText.map { it.trim() }
}
