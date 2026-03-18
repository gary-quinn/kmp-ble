package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.ServerConnection
import com.atruedev.kmpble.server.ServerConnectionEvent
import com.atruedev.kmpble.server.ServerException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/**
 * Fake GATT server for unit testing peripheral-role code.
 *
 * Mirrors the real server's contract: operations require the server to be
 * open, [notify] and [indicate] check device connection, and [indicate]
 * requires [notify]/[indicate] to target connected devices only.
 *
 * ## Example
 *
 * ```kotlin
 * val server = FakeGattServer()
 * server.open()
 *
 * server.simulateConnection(Identifier("AA:BB:CC:DD:EE:FF"), "TestDevice")
 * assertEquals(1, server.connections.value.size)
 *
 * server.notify(charUuid, null, byteArrayOf(0x01))
 * assertEquals(1, server.getNotifications().size)
 * ```
 */
public class FakeGattServer : GattServer {

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 16)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    private var _isOpen = false

    private val notifications = mutableListOf<NotificationRecord>()
    private val indications = mutableListOf<NotificationRecord>()

    override suspend fun open() {
        _isOpen = true
    }

    override suspend fun notify(characteristicUuid: Uuid, device: Identifier?, data: ByteArray) {
        checkOpen()
        if (device != null) {
            checkConnected(device)
        }
        notifications.add(NotificationRecord(characteristicUuid, device, data.copyOf()))
    }

    override suspend fun indicate(characteristicUuid: Uuid, device: Identifier, data: ByteArray) {
        checkOpen()
        checkConnected(device)
        indications.add(NotificationRecord(characteristicUuid, device, data.copyOf()))
    }

    override fun close() {
        _isOpen = false
        _connections.value = emptyList()
    }

    // --- Test helpers ---

    /** Whether the server is currently open. */
    public val isOpen: Boolean get() = _isOpen

    /** Simulate a client device connecting. */
    public suspend fun simulateConnection(device: Identifier, name: String? = null) {
        _connections.update { it + ServerConnection(device, name) }
        _connectionEvents.emit(ServerConnectionEvent.Connected(device))
    }

    /** Simulate a client device disconnecting. */
    public suspend fun simulateDisconnection(device: Identifier) {
        _connections.update { list -> list.filter { it.device != device } }
        _connectionEvents.emit(ServerConnectionEvent.Disconnected(device))
    }

    /** Get all captured notification records. */
    public fun getNotifications(): List<NotificationRecord> = notifications.toList()

    /** Get all captured indication records. */
    public fun getIndications(): List<NotificationRecord> = indications.toList()

    /** Clear all captured notifications. */
    public fun clearNotifications() {
        notifications.clear()
    }

    /** Clear all captured indications. */
    public fun clearIndications() {
        indications.clear()
    }

    public data class NotificationRecord(
        val characteristicUuid: Uuid,
        val device: Identifier?,
        val data: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as NotificationRecord
            return characteristicUuid == other.characteristicUuid &&
                device == other.device &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = characteristicUuid.hashCode()
            result = 31 * result + (device?.hashCode() ?: 0)
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private fun checkOpen() {
        if (!_isOpen) throw ServerException.NotOpen()
    }

    private fun checkConnected(device: Identifier) {
        if (_connections.value.none { it.device == device }) {
            throw ServerException.DeviceNotConnected("Device $device not connected")
        }
    }
}
