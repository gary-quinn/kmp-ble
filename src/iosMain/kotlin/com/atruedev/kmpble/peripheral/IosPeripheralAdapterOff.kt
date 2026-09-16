package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fans out adapter-off transitions to every live [IosPeripheral] so the shared state machine
 * can move to [State.Disconnected.BySystemEvent] without waiting for a late didDisconnect.
 */
internal object IosPeripheralAdapterOff {
    private val registered = atomic(false)

    fun ensureRegistered() {
        if (!registered.compareAndSet(expect = false, update = true)) return
        CentralManagerProvider.manager
        CentralManagerProvider.scanDelegate.registerAdapterOffHandler(::notifyAll)
    }

    private fun notifyAll() {
        PeripheralRegistry.forEachInitialized { peripheral ->
            if (peripheral is IosPeripheral) {
                peripheral.notifyAdapterOff()
            }
        }
    }
}

internal fun IosPeripheral.notifyAdapterOff() {
    peripheralContext.scope.launch {
        onAdapterOff()
    }
}

internal suspend fun IosPeripheral.onAdapterOff() {
    withContext(peripheralContext.dispatcher) {
        if (peripheralContext.state.value is State.Disconnected) return@withContext
        onDisconnectCleanup()
        slots.completeDisconnect()
        slots.failDiscovery(BleException(ConnectionLost("Bluetooth adapter turned off")))
        slots.completeConnect()
        peripheralContext.processEvent(ConnectionEvent.AdapterOff)
    }
}
