@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.newScanner

/**
 * Portable JVM [Scanner] factory. Delegates to the [com.atruedev.kmpble.backend.BleBackend]
 * resolved by [BleBackends]: `kmp-ble-bluez` on Linux, `kmp-ble-macos` on macOS arm64.
 *
 * Construction does not touch the Bluetooth stack; failures surface when [Scanner.scanEvents]
 * is collected. Throws [UnsupportedOperationException] when no backend supports this host.
 */
public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner =
    BleBackends.require("Scanner").newScanner(configure)
