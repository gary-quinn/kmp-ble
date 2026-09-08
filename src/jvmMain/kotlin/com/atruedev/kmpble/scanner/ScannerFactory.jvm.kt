package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.unsupportedBle

/**
 * Portable JVM [Scanner] factory. Remains disabled on CI and non-Linux hosts unless
 * [BlueZ.ENABLE_PROPERTY] is set to `"true"` **and** [BlueZ.isAvailable] reports a BlueZ adapter.
 *
 * For Linux desktop apps, prefer the explicit [BlueZScanner] factory.
 */
public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner {
    if (BlueZ.isExplicitlyEnabled() && BlueZ.isAvailable()) {
        return BlueZScanner(configure)
    }
    unsupportedBle(
        "Scanner (use BlueZScanner { } on Linux with BlueZ, or FakeScanner in tests; " +
            "set -D${BlueZ.ENABLE_PROPERTY}=true to opt in via Scanner { })",
    )
}
