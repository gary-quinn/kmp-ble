package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.unsupportedBle

/**
 * Portable JVM [Scanner] factory. Remains disabled on CI and non-Linux hosts unless
 * [BlueZ.ENABLE_PROPERTY] is set to `"true"`.
 *
 * Opt-in does not probe D-Bus; [BlueZScanner] fails cleanly at collect time when BlueZ
 * is unavailable. Use [BlueZ.isAvailable] only when an explicit availability probe is needed.
 *
 * For Linux desktop apps, prefer the explicit [BlueZScanner] factory.
 */
public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner {
    if (BlueZ.isExplicitlyEnabled()) {
        return BlueZScanner(configure)
    }
    unsupportedBle(
        "Scanner (use BlueZScanner { } on Linux with BlueZ, or FakeScanner in tests; " +
            "set -D${BlueZ.ENABLE_PROPERTY}=true to opt in via Scanner { })",
    )
}
