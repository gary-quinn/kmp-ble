package com.atruedev.kmpble.adapter

/**
 * State of the device's Bluetooth adapter.
 *
 * Observe via [BluetoothAdapter.state] to react to Bluetooth being toggled on/off,
 * permissions being revoked, or hardware availability.
 */
public sealed interface BluetoothAdapterState {
    /** Bluetooth is powered off. */
    public data object Off : BluetoothAdapterState

    /** Bluetooth is powered on and ready. */
    public data object On : BluetoothAdapterState

    /** Adapter exists but is not ready (turning on/off, resetting). */
    public data object Unavailable : BluetoothAdapterState

    /** Bluetooth permission denied at runtime. */
    public data object Unauthorized : BluetoothAdapterState

    /** Device does not have BLE hardware. */
    public data object Unsupported : BluetoothAdapterState
}
