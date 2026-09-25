plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
}

group = "com.atruedev"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

val isMacosHost = System.getProperty("os.name").startsWith("Mac")
val nativeSource = layout.projectDirectory.file("src/native/KmpBleMacos.m")
val nativeResources = layout.buildDirectory.dir("generated/nativeResources")
val nativeLibraryPath = "native/macos-arm64/libkmpble_macos.dylib"

val buildNativeLibrary by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles the CoreBluetooth JNI shim into an arm64 dylib (macOS hosts only)"
    onlyIf("requires a macOS host with Xcode command line tools") { isMacosHost }
    inputs.file(nativeSource)
    outputs.dir(nativeResources)

    val javaHome = System.getProperty("java.home")
    val output = nativeResources.map { it.file(nativeLibraryPath).asFile }
    doFirst { output.get().parentFile.mkdirs() }
    commandLine(
        "xcrun",
        "clang",
        "-arch",
        "arm64",
        "-mmacosx-version-min=11.0",
        "-dynamiclib",
        "-fobjc-arc",
        "-O2",
        "-Wall",
        "-Wextra",
        "-Wno-unused-parameter",
        "-Werror",
        "-I$javaHome/include",
        "-I$javaHome/include/darwin",
        "-framework",
        "Foundation",
        "-framework",
        "CoreBluetooth",
        "-install_name",
        "@rpath/libkmpble_macos.dylib",
        "-o",
        output.get().absolutePath,
        nativeSource.asFile.absolutePath,
    )
}

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
        jvmMain {
            resources.srcDir(nativeResources)
            dependencies {
                api(project(":"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

tasks.named("jvmProcessResources") { dependsOn(buildNativeLibrary) }

tasks.withType<Jar>().configureEach {
    if (name == "jvmJar") dependsOn(buildNativeLibrary)
}

tasks.matching { it.name.startsWith("publish") && !it.name.contains("MavenLocal") }.configureEach {
    doFirst {
        check(isMacosHost) {
            "kmp-ble-macos must be published from a macOS host so the jar contains $nativeLibraryPath"
        }
    }
}

tasks.named<Test>("jvmTest") {
    systemProperty("kmpble.macos.nativeBuilt", isMacosHost.toString())
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble-macos")
        includes.from("MODULE.md")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.atruedev", "kmp-ble-macos", version.toString())

    pom {
        name.set("kmp-ble-macos")
        description.set("macOS (arm64) CoreBluetooth backend for kmp-ble on the JVM")
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
