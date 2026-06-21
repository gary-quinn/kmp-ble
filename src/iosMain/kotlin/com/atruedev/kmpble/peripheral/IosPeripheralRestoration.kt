package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBPeripheralStateConnected

/**
 * Restore this peripheral from iOS state restoration.
 *
 * Re-populates persisted observations and triggers discovery if iOS already
 * delivered the peripheral as connected.
 */
internal suspend fun IosPeripheral.restoreFromStateRestorationExt(savedObservations: Set<PersistedObservation>) {
    if (closed) return

    withContext(peripheralContext.dispatcher) {
        for (obs in savedObservations) {
            observationManager.subscribe(obs.key.serviceUuid, obs.key.charUuid, obs.backpressure)
        }

        if (cbPeripheral.state == CBPeripheralStateConnected) {
            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start()
            peripheralContext.processEvent(ConnectionEvent.LinkEstablished)

            val deferred = slots.armConnect()
            bridge.discoverServices()
            try {
                withTimeout(currentTimeouts.serviceDiscovery) { deferred.await() }
            } finally {
                slots.clearConnect()
            }
        }
    }
}
