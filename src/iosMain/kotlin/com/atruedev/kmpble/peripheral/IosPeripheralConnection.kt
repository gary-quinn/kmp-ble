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
import platform.CoreBluetooth.CBPeripheralStateConnected
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

        // Re-affirm this instance's connection-callback registration before connecting,
        // mirroring bridge.connect()'s own re-affirmation of cbPeripheral.delegate. Guards
        // against a since-discarded duplicate IosPeripheral for this identifier (raced via
        // PeripheralRegistry.getOrCreate) having last written the shared registration.
        centralDelegate.registerConnectionCallback(identifier.value, connectionCallback)

        val deferred = slots.armConnect()
        bridge.connect()

        try {
            withTimeout(options.timeouts.connect) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            // Cancel the link and await didDisconnect BEFORE transitioning state, so the
            // timeout's disconnect is consumed before an auto-reconnect (which wakes on the
            // Disconnected emission) can start -- a stale didDisconnect would otherwise kill
            // the next connect. The didDisconnect callback skips its own transition while
            // [pendingTimeoutDisconnect] is set.
            pendingTimeoutDisconnect.value = true
            val disconnected = slots.armDisconnect()
            bridge.disconnect()
            try {
                withTimeout(DISCONNECT_TIMEOUT) { disconnected.await() }
            } catch (_: TimeoutCancellationException) {
                // didDisconnect never arrived; the transition below still fires. A late
                // delivery can still race a reconnect (see #633).
            } finally {
                slots.clearDisconnect()
            }
            pendingTimeoutDisconnect.value = false
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
        if (peripheralContext.state.value is State.Disconnected) {
            // A retrieved peripheral is OS-connected while Kotlin is Disconnected; cancel
            // and await didDisconnect so a fire-and-forget cancel can't race the next connect.
            if (cbPeripheral.state == CBPeripheralStateConnected) {
                val deferred = slots.armDisconnect()
                bridge.disconnect()
                try {
                    withTimeout(DISCONNECT_TIMEOUT) { deferred.await() }
                } catch (_: TimeoutCancellationException) {
                    // OS never confirmed; nothing further to release.
                } finally {
                    slots.clearDisconnect()
                }
            }
            return@withContext
        }
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

            val cbServices = currentServices()
            when (currentDiscoveryAction(cbServices)) {
                DiscoveryPolicy.DiscoveryAction.ReuseCache -> finishDiscoveryFromCache(cbServices)
                DiscoveryPolicy.DiscoveryAction.WaitForTable -> finishDiscoveryFromRetrievedTable()
                DiscoveryPolicy.DiscoveryAction.Rediscover -> discoverServicesSafely()
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

        // Skip the transition when it is owned elsewhere: the connect-timeout path performs
        // it after awaiting this callback, and an already-Disconnected peripheral must not
        // replay an invalid ConnectionLost (e.g. a late didDisconnect after the await expired).
        if (!pendingTimeoutDisconnect.value && peripheralContext.state.value !is State.Disconnected) {
            peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
        }
        slots.completeDisconnect()
        onDisconnectCleanup()
        // Release a discovery cycle left in flight by the disconnect, so the next
        // connect's tryArmDiscovery() isn't permanently blocked by this slot.
        slots.failDiscovery(BleException(bleError))
        slots.completeConnect()
    }
}

/**
 * A fresh `discoverServices(null)` pass. If it throws (rare - failures normally arrive via
 * the async callback), release the slots so they don't leak waiting for a callback that
 * will never come.
 */
internal suspend fun IosPeripheral.discoverServicesSafely() {
    try {
        bridge.discoverServices(discoveryGeneration.value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val failure = OperationFailed("discoverServices() failed: ${e.message}")
        peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(failure))
        slots.failDiscovery(BleException(failure))
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
