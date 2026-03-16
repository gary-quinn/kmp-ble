package com.atruedev.kmpble.adapter

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes the system Bluetooth adapter state.
 *
 * Standalone — not coupled to the connection API. When the adapter transitions to
 * [BluetoothAdapterState.Off] or [BluetoothAdapterState.Unavailable], downstream
 * peripherals (P2+) will transition to Disconnected.BySystemEvent.
 *
 * Platform construction:
 * - Android: `AndroidBluetoothAdapter(context)`
 * - iOS: `IosBluetoothAdapter()`
 */
public interface BluetoothAdapter {
    public val state: StateFlow<BluetoothAdapterState>
    public fun close()
}
