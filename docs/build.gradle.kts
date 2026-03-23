plugins {
    alias(libs.plugins.dokka)
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble")
        includes.from("MODULE.md")
    }
    pluginsConfiguration.html {
        footerMessage.set("Copyright &copy; 2025 Gary Quinn. Licensed under Apache 2.0.")
    }
}

dependencies {
    dokka(project(":"))
    dokka(project(":kmp-ble-codec"))
    dokka(project(":kmp-ble-dfu"))
    dokka(project(":kmp-ble-profiles"))
    dokka(project(":kmp-ble-quirks"))
}
