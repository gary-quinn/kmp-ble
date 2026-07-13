package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.CancellationException
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
            // Duplicate "connected" callback; a discovery cycle already in flight covers it.
            if (!slots.tryArmDiscovery()) return@launch
            // New discovery cycle on connect: increment generation and clear stale handles
            discoveryGeneration.incrementAndGet()
            nativeCharMap.clear()
            nativeDescMap.clear()
            try {
                bridge.discoverServices()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // discoverServices() is just a delegate/property assignment plus a void ObjC
                // call - failures are normally reported later via the async callback, not a
                // throw. If it does throw, the discovery slot armed above would otherwise leak
                // forever (no callback will ever arrive to release it).
                val failure = OperationFailed("discoverServices() failed: ${e.message}")
                peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(failure))
                slots.failDiscovery(BleException(failure))
                slots.completeConnect()
            }
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
        // Release a discovery cycle left in flight by the disconnect, so the next
        // connect's tryArmDiscovery() isn't permanently blocked by this slot.
        slots.failDiscovery(BleException(bleError))
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
