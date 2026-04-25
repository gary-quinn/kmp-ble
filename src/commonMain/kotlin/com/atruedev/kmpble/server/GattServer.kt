package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * A GATT server that hosts BLE services on the local device.
 *
 * The phone acts as a BLE peripheral - remote devices (centrals) can
 * connect and read/write characteristics, subscribe to notifications.
 *
 * ## Usage
 *
 * ```kotlin
 * val server = GattServer {
 *     service(myServiceUuid) {
 *         characteristic(commandUuid) {
 *             properties { write = true; writeWithoutResponse = true }
 *             permissions { write = true }
 *             onWrite { device, data, responseNeeded ->
 *                 processCommand(data)
 *                 if (responseNeeded) GattStatus.Success else null
 *             }
 *         }
 *         characteristic(statusUuid) {
 *             properties { read = true; notify = true }
 *             permissions { read = true }
 *             onRead { device -> BleData(currentStatus.toByteArray()) }
 *         }
 *     }
 * }
 *
 * server.open()
 *
 * // Notify connected devices when status changes
 * server.notify(statusUuid, device, BleData(newStatusBytes))
 *
 * // When done
 * server.close()
 * ```
 *
 * ## Lifecycle
 *
 * - Created via [GattServer] factory function (no OS resources allocated)
 * - [open] allocates OS resources and starts accepting connections
 * - [close] disconnects all clients, releases OS resources
 * - Use independently from [Advertiser] - a server can accept connections
 *   without advertising (if client already knows the address)
 */
public interface GattServer : AutoCloseable {
    /**
     * Currently connected devices.
     *
     * Emits an updated list whenever a device connects or disconnects.
     */
    public val connections: StateFlow<List<ServerConnection>>

    /**
     * Flow of connection events (connect/disconnect).
     *
     * Use for reacting to connection lifecycle:
     * ```kotlin
     * server.connectionEvents.collect { event ->
     *     when (event) {
     *         is ServerConnectionEvent.Connected -> log("${event.device} connected")
     *         is ServerConnectionEvent.Disconnected -> log("${event.device} disconnected")
     *     }
     * }
     * ```
     */
    public val connectionEvents: Flow<ServerConnectionEvent>

    /**
     * Open the GATT server and start accepting connections.
     *
     * Allocates OS resources (BluetoothGattServer on Android,
     * CBPeripheralManager on iOS). Must be called before clients
     * can connect.
     *
     * @throws ServerException.OpenFailed if server cannot be opened
     * @throws ServerException.NotSupported if GATT server not available
     */
    public suspend fun open()

    /**
     * Send a notification to a connected device.
     *
     * The characteristic must have notify property enabled, and the
     * client must have subscribed (written to CCCD).
     *
     * @param characteristicUuid UUID of the characteristic to notify on
     * @param device The connected device to notify (or null for all subscribed)
     * @param data The notification payload
     * @throws ServerException.NotOpen if server is not open
     * @throws ServerException.DeviceNotConnected if device is not connected
     */
    public suspend fun notify(
        characteristicUuid: Uuid,
        device: Identifier?,
        data: BleData,
    )

    /**
     * Send an indication (acknowledged notification) to a connected device.
     *
     * Like [notify] but the client must acknowledge receipt. Blocks until
     * acknowledgement is received or timeout.
     *
     * @param characteristicUuid UUID of the characteristic
     * @param device The connected device (required, no broadcast)
     * @param data The indication payload
     * @throws ServerException.NotOpen if server is not open
     * @throws ServerException.DeviceNotConnected if device is not connected
     */
    public suspend fun indicate(
        characteristicUuid: Uuid,
        device: Identifier,
        data: BleData,
    )

    /**
     * Close the server.
     *
     * - Disconnects all connected clients
     * - Releases OS resources
     * - [connections] emits empty list
     * - Subsequent operations throw [ServerException.NotOpen]
     *
     * Safe to call multiple times.
     */
    override fun close()
}

public data class ServerConnection(
    val device: Identifier,
    val name: String? = null,
)

public sealed interface ServerConnectionEvent {
    public data class Connected(
        val device: Identifier,
    ) : ServerConnectionEvent

    public data class Disconnected(
        val device: Identifier,
    ) : ServerConnectionEvent
}

/** Sentinel [Identifier] used in log events when [GattServer.notify] targets all subscribed centrals. */
internal val BROADCAST_IDENTIFIER = Identifier("broadcast")
