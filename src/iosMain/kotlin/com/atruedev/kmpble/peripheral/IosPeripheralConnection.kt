package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import kotlinx.coroutines.launch
import platform.Foundation.NSError

/**
 * Connection callback handling for [IosPeripheral].
 */

internal fun IosPeripheral.handleConnectionCallback(
    connected: Boolean,
    error: NSError?,
) {
    peripheralContext.scope.launch {
        if (connected) {
            peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
            // New discovery cycle on connect: increment generation and clear stale handles
            discoveryGeneration++
            nativeCharMap.clear()
            nativeDescMap.clear()
            bridge.discoverServices()
            return@launch
        }

        val bleError =
            if (error != null) {
                ConnectionFailed(error.localizedDescription, error.code.toInt())
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
