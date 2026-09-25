plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":"))
            implementation(project(":kmp-ble-bluez"))
            implementation(project(":kmp-ble-macos"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the desktop BLE sample: ./gradlew :sample-jvm:run --args=\"scan 10\""
    val main = kotlin.jvm().compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.atruedev.kmpble.sample.jvm.MainKt")
    standardInput = System.`in`
    systemProperties(providers.systemPropertiesPrefixedBy("kmpble.").get())
}
