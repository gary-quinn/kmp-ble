package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.backendHandle
import com.atruedev.kmpble.backend.peripheral
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.Advertisement

/**
 * The BlueZ [Peripheral] for this advertisement, regardless of which backend is active.
 * Uses the D-Bus device path when the advertisement came from a BlueZ scan, otherwise derives
 * it from the adapter and the identifier's MAC address.
 */
public fun Advertisement.toBlueZPeripheral(): Peripheral {
    val backend = BlueZBackend.shared
    return backend.peripheral(identifier, backendHandle(backend))
}
