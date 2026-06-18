@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.content.Context
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.toGattStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

/**
 * Android implementation of [GattServer] using [BluetoothGattServer].
 *
 * Delegates state management to [AndroidGattServerState] and callback handling
 * to [AndroidGattServerCallback]. Setup logic lives in [AndroidGattServerSetup.kt].
 *
 * ## Architecture
 *
 * - Uses [BluetoothManager.openGattServer] to create the native server
 * - Handlers (onRead/onWrite) are dispatched on a serialized dispatcher
 *   (same limitedParallelism(1) pattern as AndroidPeripheral)
 * - CCCD tracking: maintains a map of which devices are subscribed
 *   to which characteristics, for notify/indicate delivery
 *
 * ## Threading
 *
 * All mutable state is accessed exclusively on the serialized [state.dispatcher]
 * (limitedParallelism(1)). Binder-thread callbacks dispatch to [state.scope]
 * which runs on the same dispatcher.
 *
 * [close] follows the AndroidPeripheral pattern: set the closed flag,
 * close native resources synchronously (BluetoothGattServer.close() is
 * thread-safe), then cancel the scope. No runBlocking - safe to call
 * from any context including handler callbacks.
 *
 * ## Lifecycle
 *
 * Each instance is single-use: after [close], the scope is cancelled and
 * cannot be restarted. Create a new instance via [GattServer] factory to
 * reopen. This matches the Android resource lifecycle.
 *
 * ## Single Instance
 *
 * Android supports only one BluetoothGattServer per app. Opening a second
 * server while one is already open throws [ServerException.OpenFailed].
 * Use [close] to release before opening another.
 */
internal class AndroidGattServer(
    private val context: Context,
    private val serviceDefinitions: List<ServiceDefinition>,
) : GattServer {
    internal val state = AndroidGattServerState(context, serviceDefinitions)

    override val connections: StateFlow<List<ServerConnection>> = state.connections
    override val connectionEvents: Flow<ServerConnectionEvent> = state.connectionEvents

    override suspend fun open() {
        if (state.isClosed.get()) {
            throw ServerException.OpenFailed(
                "This server instance has been closed and cannot be reopened. " +
                    "Create a new instance via GattServer { ... } factory.",
            )
        }

        withContext(state.dispatcher) {
            if (state.isOpen.get()) return@withContext

            // Single-instance enforcement
            if (!instanceLock.compareAndSet(false, true)) {
                throw ServerException.OpenFailed(
                    "Another GattServer is already open. Android supports only one GATT server per app. " +
                        "Call close() on the existing server before opening a new one.",
                )
            }

            logEvent(BleLogEvent.ServerLifecycle("opening"))

            try {
                state.openInternal(instanceLock)
            } catch (e: Exception) {
                instanceLock.set(false)
                throw e
            }
        }
    }

    override suspend fun notify(
        characteristicUuid: Uuid,
        device: Identifier?,
        data: BleData,
    ) {
        withContext(state.dispatcher) {
            checkOpen()
            val server = state.nativeServer ?: throw ServerException.NotOpen()
            val nativeChar =
                state.characteristicCache[characteristicUuid]
                    ?: throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")

            val targets =
                if (device != null) {
                    // Specific device - verify connected and subscribed
                    val connected =
                        state.connectedDevices[device]
                            ?: throw ServerException.DeviceNotConnected("Device $device not connected")
                    val key = AndroidGattServerState.SubscriptionKey(characteristicUuid, device)
                    val mode = state.subscriptionModes[key]
                    if (mode == null || mode.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        return@withContext // Not subscribed, skip silently
                    }
                    listOf(connected)
                } else {
                    // All subscribed devices for this characteristic - O(1) lookup via secondary index
                    val subscribed = state.subscribersByChar[characteristicUuid] ?: emptySet()
                    subscribed.mapNotNull { state.connectedDevices[it] }
                }

            val dataBytes = data.toByteArray()

            // Warn if notification payload exceeds any target's MTU
            for (target in targets) {
                val targetId = Identifier(target.address)
                val mtu = state.deviceMtu[targetId] ?: DEFAULT_MTU
                val maxPayload = mtu - ATT_HEADER_SIZE
                if (dataBytes.size > maxPayload) {
                    logEvent(
                        BleLogEvent.Error(
                            targetId,
                            "Notification payload (${dataBytes.size}B) exceeds " +
                                "device MTU ($mtu, max payload ${maxPayload}B). " +
                                "Data will be truncated by the BLE stack.",
                            null,
                        ),
                    )
                }
            }

            targets
                .map { target ->
                    async {
                        try {
                            state.awaitNotifySend(server, target, nativeChar, dataBytes, confirm = false)
                        } catch (e: Exception) {
                            logEvent(BleLogEvent.Error(Identifier(target.address), "notify failed", e))
                        }
                    }
                }.awaitAll()

            logEvent(
                BleLogEvent.ServerRequest(
                    device ?: BROADCAST_IDENTIFIER,
                    "notify (${data.size}B to ${targets.size} devices)",
                    characteristicUuid,
                    GattStatus.Success,
                ),
            )
        }
    }

    override suspend fun indicate(
        characteristicUuid: Uuid,
        device: Identifier,
        data: BleData,
    ) {
        withContext(state.dispatcher) {
            checkOpen()
            val server = state.nativeServer ?: throw ServerException.NotOpen()
            val nativeChar =
                state.characteristicCache[characteristicUuid]
                    ?: throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")

            val target =
                state.connectedDevices[device]
                    ?: throw ServerException.DeviceNotConnected("Device $device not connected")

            // Check CCCD subscription
            val key = AndroidGattServerState.SubscriptionKey(characteristicUuid, device)
            val mode = state.subscriptionModes[key]
            if (mode == null || mode.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                throw ServerException.NotifyFailed("Device $device is not subscribed to $characteristicUuid")
            }

            val dataBytes = data.toByteArray()

            // Warn on MTU truncation
            val mtu = state.deviceMtu[device] ?: DEFAULT_MTU
            val maxPayload = mtu - ATT_HEADER_SIZE
            if (dataBytes.size > maxPayload) {
                logEvent(
                    BleLogEvent.Error(
                        device,
                        "Indication payload (${dataBytes.size}B) exceeds " +
                            "device MTU ($mtu, max payload ${maxPayload}B). " +
                            "Data will be truncated by the BLE stack.",
                        null,
                    ),
                )
            }

            try {
                val gattStatus = state.awaitNotifySend(server, target, nativeChar, dataBytes, confirm = true)
                if (gattStatus != BluetoothGatt.GATT_SUCCESS) {
                    throw ServerException.NotifyFailed(
                        "Indication failed with status ${gattStatus.toGattStatus()}",
                    )
                }
            } catch (e: SecurityException) {
                throw ServerException.NotifyFailed("Missing BLUETOOTH_CONNECT permission", e)
            }
            logEvent(
                BleLogEvent.ServerRequest(
                    device,
                    "indicate (${data.size}B)",
                    characteristicUuid,
                    GattStatus.Success,
                ),
            )
        }
    }

    /**
     * Close the server. Safe to call from any thread, including handler callbacks.
     *
     * Follows the AndroidPeripheral pattern: set closed flag, release native
     * resources synchronously (BluetoothGattServer.close() is thread-safe),
     * then cancel the scope. No runBlocking - no deadlock risk.
     *
     * Uses [AtomicBoolean.compareAndSet] to ensure exactly one close executes
     * even under concurrent calls.
     */
    override fun close() {
        if (!state.isOpen.compareAndSet(true, false)) return
        state.isClosed.set(true)
        logEvent(BleLogEvent.ServerLifecycle("closing"))

        // Close native server first - stops all Binder callbacks
        try {
            state.nativeServer?.close()
        } catch (_: SecurityException) {
            // Ignore permission errors on close
        }
        state.nativeServer = null

        // Cancel all pending notifications/indications
        for ((_, deferred) in state.pendingNotifySent) {
            deferred.cancel(CancellationException("Server closed"))
        }
        state.pendingNotifySent.clear()

        // Don't clear collections here - races with in-flight coroutines before cancellation.
        state.scope.cancel()
        state.clearConnections()

        // Release singleton lock
        instanceLock.set(false)
        logEvent(BleLogEvent.ServerLifecycle("closed"))
    }

    private fun checkOpen() {
        if (!state.isOpen.get()) throw ServerException.NotOpen()
    }

    internal companion object {
        val CCCD_UUID: Uuid = com.atruedev.kmpble.gatt.internal.CCCD_UUID
        const val NOTIFY_TIMEOUT_MS = 5_000L
        const val DEFAULT_MTU = 23
        const val ATT_HEADER_SIZE = 3

        // Global single-instance guard - Android supports one GATT server per app
        private val instanceLock = AtomicBoolean(false)
    }
}
