package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.peripheral.state.State
import kotlin.uuid.ExperimentalUuidApi

/**
 * Reconnect to this peripheral using the last-used [ConnectionOptions].
 *
 * After a successful [connect] call, the peripheral caches the options
 * used. [reconnect] retrieves those cached options, re-establishes the
 * connection, and re-discovers GATT services (since GATT state is lost
 * on disconnect).
 *
 * ```kotlin
 * peripheral.connect(ConnectionOptions.Balanced)
 * // ... work with the peripheral ...
 * peripheral.disconnect()
 * // ... later, reconnect with the same options ...
 * peripheral.reconnect()
 * ```
 *
 * Behavior:
 * - Uses the [ConnectionOptions] from the most recent [connect] call.
 * - Re-discovers GATT services after connecting (since GATT state is lost).
 * - If [connect] was never called, throws [IllegalStateException].
 *
 * @throws IllegalStateException if [connect] was never called (no cached options)
 *   or if the peripheral is already in [State.Connected.Ready].
 * @throws com.atruedev.kmpble.error.BleException if reconnection fails.
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun Peripheral.reconnect() {
    val lastOptions =
        lastConnectionOptions
            ?: throw IllegalStateException(
                "Cannot reconnect: connect() has not been called yet. " +
                    "Call connect(options) first, then use reconnect() after disconnect.",
            )

    check(state.value !is State.Connected.Ready) {
        "Cannot reconnect: peripheral is already connected"
    }

    connect(lastOptions)
    refreshServices()
}
