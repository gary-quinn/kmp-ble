package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
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
    if (_closed.value) return

    withContext(peripheralContext.dispatcher) {
        for (obs in savedObservations) {
            observationManager.subscribe(obs.key.serviceUuid, obs.key.charUuid, obs.backpressure)
        }

        if (cbPeripheral.state == CBPeripheralStateConnected) {
            // A connect callback's own discovery cycle may already cover this peripheral.
            if (!slots.tryArmDiscovery()) return@withContext

            // The discovery slot armed above only guards against that race; it isn't what we
            // wait on below. armConnect() separately blocks a concurrent explicit connect()
            // call, and its deferred is what finishDiscovery() completes once discovery
            // finishes, so it's what we await. Wrapped in try/finally so a race on armConnect()
            // itself (e.g. a concurrent connect()) still releases the discovery slot instead of
            // leaking it.
            try {
                peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
                peripheralContext.gattQueue.start()
                peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
                discoveryGeneration.incrementAndGet()
                nativeCharMap.clear()
                nativeDescMap.clear()

                val deferred = slots.armConnect()
                bridge.discoverServices()
                try {
                    withTimeout(currentTimeouts.serviceDiscovery) { deferred.await() }
                } finally {
                    slots.clearConnect()
                }
            } finally {
                slots.clearDiscovery()
            }
        }
    }
}
