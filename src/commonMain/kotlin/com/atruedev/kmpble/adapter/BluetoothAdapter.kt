package com.atruedev.kmpble.adapter

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes the system Bluetooth adapter state.
 *
 * Standalone - not coupled to the connection API. When the adapter transitions to
 * [BluetoothAdapterState.Off] or [BluetoothAdapterState.Unavailable], downstream
 * peripherals (P2+) will transition to Disconnected.BySystemEvent.
 *
 * Platform construction:
 * - Android: `AndroidBluetoothAdapter(context)`
 * - iOS: `IosBluetoothAdapter()`
 */
public interface BluetoothAdapter {
    public val state: StateFlow<BluetoothAdapterState>

    /**
     * Hardware and platform capabilities for Bluetooth 5.x features.
     *
     * Use this to gracefully degrade when a feature is unsupported rather
     * than catching errors after attempting the operation.
     */
    public val capabilities: BleCapabilities

    public fun close()
}
