package com.atruedev.kmpble.adapter

import com.atruedev.kmpble.internal.CentralManagerProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS Bluetooth adapter state observer.
 *
 * Accessing [state] lazily initializes the shared [CBCentralManager], which
 * triggers the iOS Bluetooth permission prompt on first access.
 */
public class IosBluetoothAdapter : BluetoothAdapter {
    override val state: StateFlow<BluetoothAdapterState>
        get() {
            CentralManagerProvider.manager
            return CentralManagerProvider.adapterStateFlow
        }

    override fun close() {
        // Delegates to singleton — no per-instance cleanup needed
    }
}
