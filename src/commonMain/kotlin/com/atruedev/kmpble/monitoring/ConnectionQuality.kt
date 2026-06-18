package com.atruedev.kmpble.monitoring

import com.atruedev.kmpble.peripheral.Peripheral

/**
 * Aggregated connection health statistics for a [Peripheral].
 *
 * Tracks connection lifecycle events and RSSI snapshots to provide
 * an at-a-glance quality signal. Useful for app-level telemetry,
 * debugging intermittent disconnects, and user-facing status displays.
 */
public data class ConnectionQuality(
    /** Total successful connections established. */
    val totalConnections: Int = 0,
    /** Total disconnections (all causes). */
    val totalDisconnections: Int = 0,
    /** Most recent RSSI reading, or null if never read. */
    val lastRssi: Int? = null,
    /** Whether the peripheral is currently in a Connected state. */
    val isConnected: Boolean = false,
) {
    /** Number of reconnections (disconnections that were followed by a new connection). */
    val reconnectionCount: Int get() = maxOf(0, totalConnections - 1)
}
