@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.content.Context
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.uuid.Uuid

/**
 * Mutable state for [AndroidGattServer]. Owns all connection tracking,
 * subscription management, handler registries, and lifecycle flags.
 *
 * All mutable state is accessed exclusively on [dispatcher] (limitedParallelism(1))
 * unless noted otherwise. Thread-safe fields accessed from Binder threads:
 * - [pendingNotifySent]: ConcurrentHashMap, CompletableDeferred.complete is safe
 * - [pendingServiceAdd]: atomicfu AtomicRef, CompletableDeferred.complete is safe
 * - [isOpen]/[isClosed]: AtomicBoolean for atomic visibility
 */
internal class AndroidGattServerState(
    val context: Context,
    val serviceDefinitions: List<ServiceDefinition>,
) {
    val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("GattServer"))

    // --- Flow properties ---
    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    // Use tryEmit to avoid blocking Binder callback thread on slow collectors.
    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 64)
    val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // --- Connection tracking ---
    val connectedDevices = mutableMapOf<Identifier, BluetoothDevice>()
    val deviceMtu = mutableMapOf<Identifier, Int>()

    fun addConnection(
        deviceId: Identifier,
        device: BluetoothDevice,
    ) {
        connectedDevices[deviceId] = device
        val connectionCount = connectedDevices.size
        _connections.update { list -> list + ServerConnection(deviceId, device.name) }
        logEvent(BleLogEvent.ServerClientEvent(deviceId, "connected ($connectionCount total)"))
        if (connectionCount >= CONNECTION_WARNING_THRESHOLD) {
            logEvent(
                BleLogEvent.Error(
                    deviceId,
                    "High connection count ($connectionCount). Android typically supports " +
                        "7-15 concurrent BLE connections depending on device. New connections " +
                        "may be silently rejected.",
                    null,
                ),
            )
        }
        if (!_connectionEvents.tryEmit(ServerConnectionEvent.Connected(deviceId))) {
            logEvent(
                BleLogEvent.Error(deviceId, "Connection event buffer full, event dropped", null),
            )
        }
    }

    fun removeConnection(
        deviceId: Identifier,
        deviceAddress: String,
    ) {
        connectedDevices.remove(deviceId)
        deviceMtu.remove(deviceId)
        _connections.update { list -> list.filter { it.device != deviceId } }
        logEvent(BleLogEvent.ServerClientEvent(deviceId, "disconnected"))
        if (!_connectionEvents.tryEmit(ServerConnectionEvent.Disconnected(deviceId))) {
            logEvent(
                BleLogEvent.Error(deviceId, "Connection event buffer full, event dropped", null),
            )
        }
    }

    // --- Subscription tracking ---
    data class SubscriptionKey(
        val characteristicUuid: Uuid,
        val device: Identifier,
    )

    val subscriptionModes = mutableMapOf<SubscriptionKey, ByteArray>()

    // Secondary index: characteristic UUID -> set of subscribed device identifiers.
    // Maintained in sync with subscriptionModes for O(1) broadcast notify lookup.
    val subscribersByChar = mutableMapOf<Uuid, MutableSet<Identifier>>()

    fun addSubscription(
        deviceId: Identifier,
        charUuid: Uuid,
        value: ByteArray,
    ) {
        val key = SubscriptionKey(charUuid, deviceId)
        subscriptionModes[key] = value.copyOf()
        subscribersByChar.getOrPut(charUuid) { mutableSetOf() }.add(deviceId)
        val mode =
            when {
                value.contentEquals(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                ) -> "notifications"
                value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) -> "indications"
                else -> "unknown mode"
            }
        logEvent(BleLogEvent.ServerClientEvent(deviceId, "subscribed to $charUuid ($mode)"))
    }

    fun removeSubscription(
        deviceId: Identifier,
        charUuid: Uuid,
    ) {
        val key = SubscriptionKey(charUuid, deviceId)
        subscriptionModes.remove(key)
        subscribersByChar[charUuid]?.remove(deviceId)
        logEvent(BleLogEvent.ServerClientEvent(deviceId, "unsubscribed from $charUuid"))
    }

    fun removeAllSubscriptions(deviceId: Identifier) {
        subscriptionModes.keys.removeAll { it.device == deviceId }
        for ((_, subscribers) in subscribersByChar) {
            subscribers.remove(deviceId)
        }
    }

    fun cancelPendingNotify(
        deviceAddress: String,
        message: String,
    ) {
        pendingNotifySent.remove(deviceAddress)?.cancel(CancellationException(message))
    }

    // --- Handler registries ---
    val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> BleData>()
    val writeHandlers =
        mutableMapOf<Uuid, suspend (Identifier, BleData, Boolean) -> GattStatus?>()

    // --- Prepared write buffer ---
    val preparedWriteBuffer = mutableMapOf<String, MutableList<WriteFragment>>()

    // --- Characteristic cache ---
    val characteristicCache = mutableMapOf<Uuid, BluetoothGattCharacteristic>()

    // --- Thread-safe fields ---
    val pendingNotifySent = ConcurrentHashMap<String, CompletableDeferred<Int>>()

    private val _pendingServiceAdd = atomic<CompletableDeferred<Int>?>(null)
    var pendingServiceAdd: CompletableDeferred<Int>?
        get() = _pendingServiceAdd.value
        set(value) { _pendingServiceAdd.value = value }

    // --- Native server ---
    private val _nativeServer = atomic<BluetoothGattServer?>(null)
    var nativeServer: BluetoothGattServer?
        get() = _nativeServer.value
        set(value) { _nativeServer.value = value }

    // --- Lifecycle flags ---
    val isOpen = AtomicBoolean(false)
    val isClosed = AtomicBoolean(false)

    // --- Callback ---
    val callback = AndroidGattServerCallback(this)

    // --- Helper methods for connection tracking ---
    fun tryEmitConnectionEvent(event: ServerConnectionEvent): Boolean = _connectionEvents.tryEmit(event)

    fun updateMtu(
        deviceId: Identifier,
        mtu: Int,
    ) {
        deviceMtu[deviceId] = mtu
        logEvent(BleLogEvent.ServerClientEvent(deviceId, "MTU changed to $mtu"))
    }

    fun clearConnections() {
        _connections.value = emptyList()
    }

    companion object {
        const val CONNECTION_WARNING_THRESHOLD = 7
    }
}
