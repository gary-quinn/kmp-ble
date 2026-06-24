package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBErrorConnectionFailed
import platform.CoreBluetooth.CBErrorConnectionLimitReached
import platform.CoreBluetooth.CBErrorConnectionTimeout
import platform.CoreBluetooth.CBErrorPeripheralDisconnected
import platform.Foundation.NSError

/**
 * Connection lifecycle management for [IosPeripheral].
 */

internal suspend fun IosPeripheral.connectInternal(options: ConnectionOptions) {
    checkNotClosed()
    currentTimeouts = options.timeouts
    pairingRequestHandler.setHandler(options.pairingHandler)
    reconnectionHandler.start(options)
    bondManager.start()
    withContext(peripheralContext.dispatcher) {
        peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
        peripheralContext.gattQueue.start(options.gattOperationTimeout)

        val deferred = slots.armConnect()
        bridge.connect()

        try {
            withTimeout(options.timeouts.connect) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            bridge.disconnect()
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(ConnectionFailed("Connection timeout")),
            )
        } finally {
            slots.clearConnect()
        }
    }
}

internal suspend fun IosPeripheral.disconnectInternal() {
    checkNotClosed()
    reconnectionHandler.stop()
    bondManager.stop()
    withContext(peripheralContext.dispatcher) {
        if (peripheralContext.state.value is State.Disconnected) return@withContext
        peripheralContext.processEvent(ConnectionEvent.DisconnectRequested)
        val deferred = slots.armDisconnect()
        bridge.disconnect()

        try {
            withTimeout(DISCONNECT_TIMEOUT) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(OperationFailed("Disconnect timeout")),
            )
        } finally {
            slots.clearDisconnect()
        }
    }
}

internal fun IosPeripheral.handleConnectionCallback(
    connected: Boolean,
    error: NSError?,
) {
    peripheralContext.scope.launch {
        if (connected) {
            peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
            // New discovery cycle on connect: increment generation and clear stale handles
            discoveryGeneration.incrementAndGet()
            nativeCharMap.clear()
            nativeDescMap.clear()
            bridge.discoverServices()
            return@launch
        }

        val bleError =
            if (error != null) {
                ConnectionFailed(
                    error.localizedDescription,
                    error.toConnectionFailureReason(),
                    error.code.toInt(),
                )
            } else {
                ConnectionLost("Disconnected")
            }

        if (peripheralContext.state.value is State.Disconnecting.Requested) {
            peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
            slots.completeDisconnect()
        } else {
            peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
        }
        onDisconnectCleanup()
        slots.completeConnect()
    }
}

internal fun NSError.toConnectionFailureReason(): ConnectionFailureReason {
    val code = this.code.toInt()
    return when (code) {
        CBErrorConnectionTimeout.toInt() -> ConnectionFailureReason.TIMEOUT
        CBErrorPeripheralDisconnected.toInt() -> ConnectionFailureReason.LINK_LOSS
        CBErrorConnectionFailed.toInt() -> ConnectionFailureReason.GATT_ERROR
        CBErrorConnectionLimitReached.toInt() -> ConnectionFailureReason.CONNECTION_REJECTED
        else -> ConnectionFailureReason.UNKNOWN
    }
}
