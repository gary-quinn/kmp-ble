package com.atruedev.kmpble.adapter

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
