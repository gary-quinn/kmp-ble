plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

group = "com.atruedev"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

kotlin {
    explicitApi()

    android {
        namespace = "com.atruedev.kmpble.quirks"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        withHostTestBuilder {}.configure {}
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":"))
        }
    }
}

dependencies {
    "androidHostTestImplementation"(libs.kotlin.test)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.atruedev", "kmp-ble-quirks", version.toString())

    pom {
        name.set("kmp-ble-quirks")
        description.set("Curated OEM device quirks for kmp-ble")
        url.set("https://github.com/atruedeveloper/kmp-ble")
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
            url.set("https://github.com/atruedeveloper/kmp-ble")
            connection.set("scm:git:git://github.com/atruedeveloper/kmp-ble.git")
            developerConnection.set("scm:git:ssh://github.com/atruedeveloper/kmp-ble.git")
        }
    }
}
