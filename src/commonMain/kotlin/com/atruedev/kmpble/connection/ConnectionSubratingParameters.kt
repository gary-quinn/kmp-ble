package com.atruedev.kmpble.connection

/**
 * LE Connection Subrating parameters for Bluetooth 5.3+ peripherals.
 *
 * Connection Subrating allows a peripheral to switch to a lower subrated
 * connection interval during idle periods, then snap back to the full
 * connection interval for data transfer. This provides better power
 * efficiency while maintaining fast response times.
 *
 * ## Platform support
 *
 * - **Android**: API 33+ via [android.bluetooth.BluetoothGatt.requestConnectionSubrating].
 *   The result is delivered asynchronously via [onSubrateChange].
 * - **iOS**: CoreBluetooth handles connection subrating internally; this API
 *   returns [com.atruedev.kmpble.connection.ConnectionSubratingResult.NotSupported].
 *
 * ## Parameter constraints (BT Core Spec v5.3)
 *
 * - [subrateFactor]: Subrate factor applied to the connection interval.
 *   Valid range 1..471.
 * - [subrateLatency]: Peripheral latency in subrated intervals (0..31).
 * - [continuationNumber]: Number of consecutive full-rate connection events
 *   after the peripheral has data (0..31).
 * - [supervisionTimeout]: Link supervision timeout in 10 ms units (10..3200).
 *   Must be > `(1 + subrateLatency) * (connInterval / subrateFactor) * 2`.
 *
 * @property subrateFactor Division factor applied to the connection interval
 *   for the subrated connection (1..471).
 * @property subrateLatency Number of subrated intervals the peripheral may
 *   skip with no data (0..31, 0 for no latency).
 * @property continuationNumber Full-rate events following a data
 *   transmission before returning to subrated interval (0..31).
 * @property supervisionTimeout Link supervision timeout in 10 ms units
 *   (10..3200).
 */
public data class ConnectionSubratingParameters(
    val subrateFactor: Int,
    val subrateLatency: Int,
    val continuationNumber: Int,
    val supervisionTimeout: Int,
) {
    init {
        require(subrateFactor in 1..471) {
            "subrateFactor must be 1..471, was $subrateFactor"
        }
        require(subrateLatency in 0..31) {
            "subrateLatency must be 0..31, was $subrateLatency"
        }
        require(continuationNumber in 0..31) {
            "continuationNumber must be 0..31, was $continuationNumber"
        }
        require(supervisionTimeout in 10..3200) {
            "supervisionTimeout must be 10..3200, was $supervisionTimeout"
        }
    }
}
