pluginManagement {
    val kotlinVersion =
        file("gradle/libs.versions.toml")
            .readLines()
            .asSequence()
            .map { it.trimStart() }
            .mapNotNull { line ->
                Regex("""^kotlin\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
            }
            .firstOrNull()
            ?: error("Version 'kotlin' not found in gradle/libs.versions.toml")
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("org.jetbrains.kotlin")) {
                useVersion(kotlinVersion)
            }
        }
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
    }
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
    }
}

rootProject.name = "kmp-ble"

include(":kmp-ble-benchmark")
include(":kmp-ble-codec")
include(":kmp-ble-codec-serialization")
include(":kmp-ble-quirks")
include(":kmp-ble-dfu")
include(":kmp-ble-profiles")
include(":kmp-ble-mesh")
include(":docs")
include(":sample")
include(":sample-android")
