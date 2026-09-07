pluginManagement {
    // Gradle evaluates pluginManagement before any other settings code, so buildSrc
    // is unavailable here. Parse only kotlin from [versions]; buildscript patches
    // use buildSrc/CatalogVersions.kt, which mirrors this format.
    val kotlinVersion =
        run {
            var inVersionsSection = false
            for (line in settings.rootDir.resolve("gradle/libs.versions.toml").readLines()) {
                val trimmed = line.trim()
                when {
                    trimmed == "[versions]" -> inVersionsSection = true
                    inVersionsSection && trimmed.startsWith("[") -> break
                    inVersionsSection -> {
                        val match = Regex("""^kotlin\s*=\s*"([^"]+)"""").find(trimmed)
                        if (match != null) {
                            return@run match.groupValues[1]
                        }
                    }
                }
            }
            error("Version 'kotlin' not found in gradle/libs.versions.toml [versions]")
        }
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
