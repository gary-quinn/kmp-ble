package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal suspend fun BlueZPeripheral.connectInternal(options: ConnectionOptions) {
    checkNotClosed()
    withContext(peripheralContext.dispatcher) {
        currentTimeouts = options.timeouts
        peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
        peripheralContext.gattQueue.start(options.gattOperationTimeout)

        val connectDeferred = slots.armConnect()
        val discoveryDeferred = slots.armDiscovery()
        try {
            ensureSessionOpen()
            val session = requireSession()
            if (!session.registerHandlers(::handleDevicePropertiesChanged, ::handleGattPropertiesChanged)) {
                throw connectFailure("Failed to register BlueZ property handlers")
            }

            session.connect().getOrElse { error ->
                throw connectFailure("BlueZ connect failed: ${error.message}")
            }

            if (!awaitCondition(options.timeouts.connect.inWholeMilliseconds) { session.isConnected() }) {
                throw connectFailure("Connection timeout after ${options.timeouts.connect}")
            }

            peripheralContext.processEvent(ConnectionEvent.LinkEstablished)

            if (!awaitCondition(
                    options.timeouts.serviceDiscovery.inWholeMilliseconds,
                ) { session.isServicesResolved() }
            ) {
                val failure = OperationFailed("Service resolution timeout after ${options.timeouts.serviceDiscovery}")
                peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(failure))
                slots.failDiscovery(BleException(failure))
                slots.completeConnect()
                throw BleException(failure)
            }

            awaitAndFinishDiscovery()
            withTimeout(options.timeouts.connect + options.timeouts.serviceDiscovery) {
                connectDeferred.await()
                discoveryDeferred.await()
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is BleException) throw error
            throw connectFailure(error.message ?: "connect failed")
        } finally {
            slots.clearConnect()
            slots.clearDiscovery()
        }
    }
}

internal suspend fun BlueZPeripheral.disconnectInternal() {
    checkNotClosed()
    withContext(peripheralContext.dispatcher) {
        if (peripheralContext.state.value is State.Disconnected) return@withContext
        peripheralContext.processEvent(ConnectionEvent.DisconnectRequested)
        slots.armDisconnect()
        val session = sessionOrNull()
        session?.disconnect()
        try {
            withTimeout(BLUEZ_DISCONNECT_TIMEOUT) {
                if (session != null) {
                    awaitCondition(BLUEZ_DISCONNECT_TIMEOUT.inWholeMilliseconds) { !session.isConnected() }
                }
            }
            handleLinkDown(disconnectRequested = true)
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(OperationFailed("Disconnect timeout")),
            )
            slots.completeDisconnect()
        } finally {
            slots.clearDisconnect()
            session?.unregisterHandlers()
        }
    }
}

internal suspend fun BlueZPeripheral.ensureSessionOpen() {
    if (sessionOrNull() != null) return
    when (val opened = sessionFactory.open(devicePath, address)) {
        is BlueZDeviceSessionOpenResult.Failed ->
            throw BleException(
                ConnectionFailed(opened.message, ConnectionFailureReason.UNKNOWN_DEVICE, opened.code),
            )
        is BlueZDeviceSessionOpenResult.Ready -> deviceSession = opened.session
    }
}

private suspend fun BlueZPeripheral.connectFailure(message: String): BleException {
    peripheralContext.processEvent(
        ConnectionEvent.ConnectionLost(
            ConnectionFailed(message, ConnectionFailureReason.GATT_ERROR),
        ),
    )
    slots.completeConnect()
    return BleException(ConnectionFailed(message, ConnectionFailureReason.GATT_ERROR))
}

private suspend fun BlueZPeripheral.awaitCondition(
    timeoutMs: Long,
    predicate: () -> Boolean,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return true
        delay(CONNECT_POLL_MS)
    }
    return predicate()
}

private const val CONNECT_POLL_MS = 50L
