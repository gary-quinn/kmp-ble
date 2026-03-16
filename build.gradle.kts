plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

group = "io.github.garyquinn"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }

    android {
        namespace = "io.github.garyquinn.kmpble"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        withHostTestBuilder {}.configure {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KmpBle"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core)
        }
    }
}

tasks.register<Exec>("assembleXCFramework") {
    dependsOn("linkReleaseFrameworkIosArm64", "linkReleaseFrameworkIosSimulatorArm64")
    group = "build"
    description = "Assembles KmpBle.xcframework from iOS release frameworks"

    val outputDir = layout.buildDirectory.dir("XCFrameworks/release")
    val arm64 = layout.buildDirectory.dir("bin/iosArm64/releaseFramework/KmpBle.framework")
    val sim = layout.buildDirectory.dir("bin/iosSimulatorArm64/releaseFramework/KmpBle.framework")

    doFirst {
        outputDir.get().asFile.let { dir ->
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }

    commandLine(
        "xcodebuild", "-create-xcframework",
        "-framework", arm64.map { it.asFile.absolutePath }.get(),
        "-framework", sim.map { it.asFile.absolutePath }.get(),
        "-output", outputDir.map { File(it.asFile, "KmpBle.xcframework").absolutePath }.get(),
    )
}

publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://central.sonatype.com/api/v1/publisher/deployments/download/")
            credentials {
                username = System.getenv("MAVEN_CENTRAL_USERNAME") ?: ""
                password = System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
            }
        }
    }

    publications.withType<MavenPublication> {
        pom {
            name.set("kmp-ble")
            description.set("Kotlin Multiplatform BLE library for Android and iOS")
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
}

signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassphrase = System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassphrase != null) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications)
    }
}
