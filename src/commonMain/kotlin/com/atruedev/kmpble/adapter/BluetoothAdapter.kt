package com.atruedev.kmpble.adapter

import com.atruedev.kmpble.Identifier
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

    /**
     * Returns the set of currently bonded (paired) devices on this device.
     *
     * Bonded devices have exchanged link keys and can establish encrypted
     * connections without re-pairing. Use this to build "reconnect to known
     * device" flows without scanning.
     *
     * **Android**: Maps to `BluetoothAdapter.getBondedDevices()`. Returns
     * the system's bonded device list, which persists across app restarts.
     *
     * **iOS**: Returns an empty list. CoreBluetooth does not expose a
     * system-wide bonded device API; users manage bonds via Settings > Bluetooth.
     */
    public fun getBondedDevices(): List<Identifier>

    public fun close()
}
