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

val sampleLibs =
    tasks.register<Sync>("installSampleLibs") {
        group = "distribution"
        description = "Copies the sample jar and its runtime classpath to build/sample/lib (java -cp 'lib/*')"
        from(tasks.named("jvmJar"))
        from(configurations.named("jvmRuntimeClasspath"))
        into(layout.buildDirectory.dir("sample/lib"))
    }

val withUsageDescription =
    providers
        .gradleProperty("kmpble.sample.usageDescription")
        .map(String::toBoolean)
        .getOrElse(false)
val macAppDir =
    layout.buildDirectory.dir(
        if (withUsageDescription) "jpackage/with-usage-description" else "jpackage/without-usage-description",
    )

val cleanMacApp = tasks.register<Delete>("cleanMacApp") { delete(macAppDir) }

tasks.register<Exec>("packageMacApp") {
    group = "distribution"
    description =
        "Packages the sample as KmpBleSample.app with jpackage (TESTING.md D10); " +
        "-Pkmpble.sample.usageDescription=true adds NSBluetoothAlwaysUsageDescription"
    dependsOn(sampleLibs, cleanMacApp)
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    val input = sampleLibs.map { it.destinationDir.path }
    val mainJar = tasks.named<Jar>("jvmJar").flatMap { it.archiveFileName }
    val resources = file("packaging/macos").path
    val javaOptions = providers.systemPropertiesPrefixedBy("kmpble.").get().map { (key, value) -> "-D$key=$value" }
    executable = "${System.getProperty("java.home")}/bin/jpackage"
    argumentProviders.add(
        CommandLineArgumentProvider {
            buildList {
                addAll(listOf("--type", "app-image", "--name", "KmpBleSample", "--app-version", "1.0"))
                addAll(listOf("--mac-package-identifier", "com.atruedev.kmpble.sample"))
                addAll(listOf("--input", input.get(), "--main-jar", mainJar.get()))
                addAll(listOf("--main-class", "com.atruedev.kmpble.sample.jvm.MainKt"))
                addAll(listOf("--dest", macAppDir.get().asFile.path))
                if (withUsageDescription) addAll(listOf("--resource-dir", resources))
                javaOptions.forEach { addAll(listOf("--java-options", it)) }
            }
        },
    )
}
