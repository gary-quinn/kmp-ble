package com.atruedev.kmpble.connection

import kotlin.time.Duration

/**
 * LE connection parameters for a parameter update request.
 *
 * The peripheral (slave) may request updated connection parameters when its
 * latency, throughput, or power requirements change after the initial
 * connection setup. The central may accept, reject, or negotiate alternative
 * values.
 *
 * ## Parameter constraints (BT Core Spec v5.0+)
 *
 * - [intervalRange]: 7.5 ms to 4 s, in 1.25 ms units. The peripheral proposes
 *   a range and the central picks a value within it. The actual negotiated
 *   interval is reported in [ConnectionParameterUpdateResult].
 * - [slaveLatency]: 0 to 499. Number of connection events the peripheral may
 *   skip when it has no data to send. Higher values save power at the cost
 *   of responsiveness.
 * - [supervisionTimeout]: 100 ms to 32 s, in 10 ms units. Link supervision
 *   timeout. Must be greater than `(1 + slaveLatency) * intervalMax * 2`.
 *   The connection is considered lost if no valid packet is received within
 *   this window.
 *
 * ## Platform notes
 *
 * - **Android**: Maps to [android.bluetooth.BluetoothGatt.requestConnectionPriority].
 *   Only three priority levels are available; the interval range is mapped to
 *   the closest supported level. The [onConnectionUpdated] callback (API 29+)
 *   reports the actual negotiated values.
 * - **iOS**: CoreBluetooth does not expose connection parameter negotiation
 *   through public API. The method returns `null`.
 *
 * @property intervalRange Preferred connection interval range (min..max).
 *   15.milliseconds..30.milliseconds is typical for responsive applications.
 * @property slaveLatency Peripheral latency (connection events to skip),
 *   0 for lowest latency.
 * @property supervisionTimeout Link supervision timeout before connection
 *   is declared lost. Must exceed `(1 + slaveLatency) * intervalRange.endInclusive * 2`.
 */
public data class ConnectionParameters(
    val intervalRange: ClosedRange<Duration>,
    val slaveLatency: Int,
    val supervisionTimeout: Duration,
) {
    init {
        require(intervalRange.start.isPositive()) {
            "intervalRange start must be positive, was ${intervalRange.start}"
        }
        require(intervalRange.endInclusive >= intervalRange.start) {
            "intervalRange endInclusive must be >= start, was ${intervalRange.endInclusive} < ${intervalRange.start}"
        }
        require(slaveLatency in 0..499) {
            "slaveLatency must be 0..499, was $slaveLatency"
        }
        require(supervisionTimeout.isPositive()) {
            "supervisionTimeout must be positive, was $supervisionTimeout"
        }
    }
}

/**
 * Result of a [com.atruedev.kmpble.peripheral.Peripheral.requestConnectionParameterUpdate]
 * request, reporting the parameters the central actually negotiated.
 */
public data class ConnectionParameterUpdateResult(
    val negotiatedInterval: Duration,
    val negotiatedLatency: Int,
    val negotiatedSupervisionTimeout: Duration,
)
