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

include(":kmp-ble-codec")
include(":kmp-ble-quirks")
include(":kmp-ble-dfu")
include(":kmp-ble-profiles")
include(":docs")
include(":sample")
include(":sample-quickstart")
include(":sample-android")
