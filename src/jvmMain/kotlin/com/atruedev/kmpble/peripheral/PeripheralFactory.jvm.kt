package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.BlueZ
import com.atruedev.kmpble.unsupportedBle

/**
 * Portable JVM [Peripheral] factory. Remains disabled on CI unless
 * [BlueZ.ENABLE_PROPERTY] is set to `"true"`.
 *
 * For Linux desktop apps, prefer [Advertisement.toBlueZPeripheral] from a [BlueZScanner]
 * advertisement, or construct [BlueZPeripheral] directly with a known D-Bus device path.
 */
public actual fun Advertisement.toPeripheral(): Peripheral {
    if (BlueZ.isExplicitlyEnabled()) {
        return toBlueZPeripheral()
    }
    unsupportedBle(
        "Peripheral (use Advertisement.toBlueZPeripheral() from BlueZScanner on Linux, " +
            "or FakePeripheral in tests; set -D${BlueZ.ENABLE_PROPERTY}=true to opt in via toPeripheral())",
    )
}
