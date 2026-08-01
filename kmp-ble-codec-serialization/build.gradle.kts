plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    android {
        namespace = "com.atruedev.kmpble.codec.serialization"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        withHostTestBuilder {}.configure {}
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-ble-codec"))
            api(libs.kotlinx.serialization.cbor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble-codec-serialization")
        includes.from("MODULE.md")
    }
}

