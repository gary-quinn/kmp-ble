package com.atruedev.kmpble.connection

/**
 * Connection priority hint requested from the central toward the peripheral.
 *
 * The hint translates to a request for new LE connection parameters (interval,
 * latency, supervision timeout). The peripheral may accept, reject, or
 * negotiate alternative values; there is no guarantee of a specific interval.
 *
 * Maps to `BluetoothGatt.CONNECTION_PRIORITY_*` on Android. iOS has no public
 * CoreBluetooth API; calls are a no-op there and return `false`.
 *
 * - [Balanced]: ~30-50 ms interval. Android default.
 * - [High]: ~11.25-15 ms interval. Highest throughput, highest power draw.
 *   Recommended for short bursts (firmware updates, L2CAP blob transfers).
 * - [LowPower]: ~100-125 ms interval. Lowest power, slow throughput.
 */
public enum class ConnectionPriority {
    Balanced,
    High,
    LowPower,
}
