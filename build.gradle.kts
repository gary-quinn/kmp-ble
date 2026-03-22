plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

group = "com.atruedev"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-opt-in=kotlinx.cinterop.BetaInteropApi")
                    freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
                }
            }
        }
    }

    android {
        namespace = "com.atruedev.kmpble"
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
            implementation(libs.androidx.startup)
            runtimeOnly(project(":kmp-ble-quirks"))
        }
    }
}

// KMP 2.1+ new Android DSL absorbs the AGP extension, so consumerProguardFiles
// isn't directly configurable. Inject the rules into the AAR at bundle time.
tasks.withType<Zip>().matching { it.name == "bundleAndroidMainAar" }.configureEach {
    from("src/androidMain/consumer-rules.pro") {
        rename { "proguard.txt" }
    }
}

tasks.register("assembleXCFramework") {
    dependsOn(
        "linkReleaseFrameworkIosArm64",
        "linkReleaseFrameworkIosSimulatorArm64",
        "linkReleaseFrameworkIosX64",
    )
    group = "build"
    description = "Assembles KmpBle.xcframework from iOS release frameworks"

    val outputDir = layout.buildDirectory.dir("XCFrameworks/release")
    val arm64 = layout.buildDirectory.dir("bin/iosArm64/releaseFramework/KmpBle.framework")
    val simArm64 = layout.buildDirectory.dir("bin/iosSimulatorArm64/releaseFramework/KmpBle.framework")
    val simX64 = layout.buildDirectory.dir("bin/iosX64/releaseFramework/KmpBle.framework")
    val fatSim = layout.buildDirectory.dir("bin/iosSimulatorFat/releaseFramework/KmpBle.framework")

    doLast {
        outputDir.get().asFile.let { dir -> dir.deleteRecursively(); dir.mkdirs() }

        val fatDir = fatSim.get().asFile
        fatDir.deleteRecursively()
        simArm64.get().asFile.copyRecursively(fatDir, overwrite = true)

        fun run(vararg args: String) {
            val result = ProcessBuilder(*args).inheritIO().start().waitFor()
            require(result == 0) { "${args.first()} failed with exit code $result" }
        }

        run(
            "lipo", "-create",
            File(simArm64.get().asFile, "KmpBle").absolutePath,
            File(simX64.get().asFile, "KmpBle").absolutePath,
            "-output", File(fatDir, "KmpBle").absolutePath,
        )

        run(
            "xcodebuild", "-create-xcframework",
            "-framework", arm64.get().asFile.absolutePath,
            "-framework", fatDir.absolutePath,
            "-output", File(outputDir.get().asFile, "KmpBle.xcframework").absolutePath,
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.atruedev", "kmp-ble", version.toString())

    pom {
        name.set("kmp-ble")
        description.set("Kotlin Multiplatform BLE library for Android, iOS, and JVM")
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
