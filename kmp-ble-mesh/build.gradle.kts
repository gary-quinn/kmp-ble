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

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.atruedev.kmpble.mesh"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        withHostTestBuilder {}.configure {}
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KmpBleMesh"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble-mesh")
        includes.from("MODULE.md")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.atruedev", "kmp-ble-mesh", version.toString())

    pom {
        name.set("kmp-ble-mesh")
        description.set("BLE Mesh networking support for kmp-ble - provisioning, proxy, models")
        url.set("https://github.com/gary-quinn/kmp-ble")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("gary-quinn")
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
