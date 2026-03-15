plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
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
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/gary-quinn/kmp-ble")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
