// kotlin-dsl resolves the Kotlin Gradle plugin embedded in Gradle, which lags the
// catalog and carries known CVEs. Align it with the catalog's kotlin version.
buildscript {
    val kotlinVersion =
        Regex("""(?m)^kotlin\s*=\s*"([^"]+)"""")
            .find(rootDir.resolve("../gradle/libs.versions.toml").readText())
            ?.groupValues
            ?.get(1)
            ?: error("Version 'kotlin' not found in gradle/libs.versions.toml")
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(kotlinVersion)
        }
    }
}

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
