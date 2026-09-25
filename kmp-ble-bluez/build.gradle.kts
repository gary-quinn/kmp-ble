plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
}

group = "com.atruedev"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-opt-in=com.atruedev.kmpble.backend.KmpBleBackendApi")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            api(project(":"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bluez.dbus)
            implementation(libs.dbus.java.transport.junixsocket)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            runtimeOnly(libs.slf4j.nop)
        }
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble-bluez")
        includes.from("MODULE.md")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.atruedev", "kmp-ble-bluez", version.toString())

    pom {
        name.set("kmp-ble-bluez")
        description.set("Linux BlueZ (D-Bus) backend for kmp-ble on the JVM")
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
