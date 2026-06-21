package com.atruedev.kmpble.quirks

/**
 * On JVM, there is no real device hardware - return a generic identity.
 * The quirk registry will resolve all defaults with no device-specific overrides.
 */
internal actual fun platformCurrentDevice(): DeviceInfo =
    DeviceInfo(
        manufacturer = "jvm",
        model = System.getProperty("os.name", "unknown").lowercase(),
        display = System.getProperty("os.version", "unknown").lowercase(),
    )
