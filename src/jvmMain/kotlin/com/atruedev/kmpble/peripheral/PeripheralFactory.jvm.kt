@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.internal.BackendAdvertisementContext
import com.atruedev.kmpble.backend.peripheral
import com.atruedev.kmpble.scanner.Advertisement

/**
 * Portable JVM [Peripheral] factory.
 *
 * An advertisement produced by a JVM backend scanner connects through that backend. Any
 * other advertisement (for example one built from a stored identifier) uses the backend
 * resolved by [BleBackends]. Throws [UnsupportedOperationException] when no backend
 * supports this host.
 */
public actual fun Advertisement.toPeripheral(): Peripheral {
    val context = platformContext as? BackendAdvertisementContext
    if (context != null) return context.backend.peripheral(identifier, context.handle)
    return BleBackends.require("Peripheral").peripheral(identifier)
}
