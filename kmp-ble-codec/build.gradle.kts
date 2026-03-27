plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
}

group = "com.atruedev"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

kotlin {
    explicitApi()

    android {
        namespace = "com.atruedev.kmpble.codec"
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
            api(project(":"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble-codec")
        includes.from("MODULE.md")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.atruedev", "kmp-ble-codec", version.toString())

    pom {
        name.set("kmp-ble-codec")
        description.set("Format-agnostic codec layer for kmp-ble typed serialization")
        url.set("https://github.com/gary-quinn/kmp-ble")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("atruedeveloper")
                name.set("Gary Quinn")
                email.set("gary@atruedev.com")
            }
        }
        scm {
            url.set("https://github.com/gary-quinn/kmp-ble")
            connection.set("scm:git:git://github.com/gary-quinn/kmp-ble.git")
            developerConnection.set("scm:git:ssh://github.com/gary-quinn/kmp-ble.git")
        }
    }
}
