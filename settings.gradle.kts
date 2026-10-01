/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

rootProject.name = "DRS-Smart-Keyboard"

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
        // Uncomment the following if testing snapshots from Maven Central
        // maven("https://central.sonatype.com/repository/maven-snapshots/")
        // Uncomment the following if testing snapshots from Maven Local
        // mavenLocal()
    }

    versionCatalogs {
        create("tools") {
            from(files("gradle/tools.versions.toml"))
        }
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":benchmark")
include(":lib:android")
include(":lib:color")
include(":lib:compose")
include(":lib:jetpref:datastore-model")
include(":lib:jetpref:datastore-model-processor")
include(":lib:jetpref:datastore-ui")
include(":lib:jetpref:material-ui")
include(":lib:kotlin")
include(":lib:snygg")
