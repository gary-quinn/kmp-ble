package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * Connect, execute [block] in the Ready state, then disconnect and close.
 *
 * The "one quick read" pattern:
 * ```
 * advertisement.toPeripheral().whenReady {
 *     val value = read(findCharacteristic(svcUuid, charUuid)!!)
 *     println("Battery: ${value[0]}%")
 * }
 * ```
 *
 * Behavior:
 * - Delegates state validation to [Peripheral.connect] - if the peripheral is already connected
 *   or connecting, [Peripheral.connect]'s own invariants apply
 * - If connection drops mid-block: the block's coroutine is cancelled with
 *   [kotlinx.coroutines.CancellationException], then close() runs in finally
 * - [Peripheral.close] always runs in a [NonCancellable] context, guaranteeing cleanup
 *   even if the coroutine is cancelled mid-block
 *
 * Not thread-safe - callers must ensure exclusive access to this peripheral.
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun Peripheral.whenReady(
    options: ConnectionOptions = ConnectionOptions(),
    block: suspend Peripheral.() -> Unit,
) {
    try {
        connect(options)
        block()
    } finally {
        withContext(NonCancellable) { close() }
    }
}

/**
 * Connect to a peripheral and automatically discover its GATT services.
 *
 * Combines the two-step connect-then-discover workflow into a single call,
 * returning the discovered services on success and cleaning up the
 * connection if discovery fails.
 *
 * ```kotlin
 * val services = peripheral.connectAndDiscover(
 *     ConnectionOptions.Balanced,
 * )
 * services.forEach { svc ->
 *     println("Service: ${svc.uuid}")
 * }
 * ```
 *
 * @param options Connection configuration. Timeouts for both connect and
 *   service discovery phases are taken from [ConnectionOptions.timeouts].
 * @return The list of discovered GATT services.
 * @throws BleException if connection fails.
 * @throws BleException if service discovery fails (connection is released).
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun Peripheral.connectAndDiscover(
    options: ConnectionOptions = ConnectionOptions(),
): List<DiscoveredService> {
    connect(options)
    return try {
        refreshServices()
    } catch (e: Exception) {
        // Release connection on discovery failure so the peripheral
        // is not left in a half-connected state.
        withContext(NonCancellable) { disconnect() }
        throw e
    }
}
