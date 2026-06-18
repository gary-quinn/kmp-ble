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
    }
}

rootProject.name = "kmp-ble"

include(":kmp-ble-benchmark")
include(":kmp-ble-codec")
include(":kmp-ble-codec-serialization")
include(":kmp-ble-quirks")
include(":kmp-ble-dfu")
include(":kmp-ble-profiles")
include(":docs")
include(":sample")
include(":sample-android")
