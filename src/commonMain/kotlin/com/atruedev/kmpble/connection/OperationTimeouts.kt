package com.atruedev.kmpble.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Per-operation timeout configuration for BLE operations.
 *
 * Each timeout applies to a single operation. [Duration.ZERO] or
 * [Duration.INFINITE] disables the timeout for that operation,
 * allowing it to run unbounded.
 *
 * Pass to [ConnectionOptions.timeouts] to override defaults for
 * specific device categories:
 * ```kotlin
 * peripheral.connect(ConnectionOptions(
 *     timeouts = OperationTimeouts(
 *         connect = 60.seconds, // slow medical device
 *         serviceDiscovery = 20.seconds,
 *     ),
 * ))
 * ```
 */
public data class OperationTimeouts(
    /** Maximum time to establish a BLE connection. Default: 30s. */
    val connect: Duration = 30.seconds,

    /** Maximum time for GATT service/characteristic discovery. Default: 15s. */
    val serviceDiscovery: Duration = 15.seconds,

    /** Maximum time for a single GATT characteristic read. Default: 5s. */
    val read: Duration = 5.seconds,

    /** Maximum time for a single GATT characteristic write. Default: 5s. */
    val write: Duration = 5.seconds,

    /** Maximum time for ATT MTU negotiation. Default: 10s. */
    val mtuNegotiation: Duration = 10.seconds,

    /** Maximum time to establish an L2CAP CoC channel. Default: 10s. */
    val l2capOpen: Duration = 10.seconds,
) {
    init {
        require(connect.isPositive() || connect == Duration.ZERO || connect == Duration.INFINITE) {
            "connect timeout must be positive, ZERO, or INFINITE, was $connect"
        }
        require(serviceDiscovery.isPositive() || serviceDiscovery == Duration.ZERO || serviceDiscovery == Duration.INFINITE) {
            "serviceDiscovery timeout must be positive, ZERO, or INFINITE, was $serviceDiscovery"
        }
        require(read.isPositive() || read == Duration.ZERO || read == Duration.INFINITE) {
            "read timeout must be positive, ZERO, or INFINITE, was $read"
        }
        require(write.isPositive() || write == Duration.ZERO || write == Duration.INFINITE) {
            "write timeout must be positive, ZERO, or INFINITE, was $write"
        }
        require(mtuNegotiation.isPositive() || mtuNegotiation == Duration.ZERO || mtuNegotiation == Duration.INFINITE) {
            "mtuNegotiation timeout must be positive, ZERO, or INFINITE, was $mtuNegotiation"
        }
        require(l2capOpen.isPositive() || l2capOpen == Duration.ZERO || l2capOpen == Duration.INFINITE) {
            "l2capOpen timeout must be positive, ZERO, or INFINITE, was $l2capOpen"
        }
    }
}
