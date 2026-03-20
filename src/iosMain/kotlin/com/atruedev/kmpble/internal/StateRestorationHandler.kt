package com.atruedev.kmpble.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.IosPeripheral
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBPeripheral
import kotlin.uuid.ExperimentalUuidApi

/**
 * Handles iOS Core Bluetooth state restoration.
 *
 * When iOS relaunches the app after termination:
 * 1. willRestoreState provides previously connected CBPeripherals
 * 2. This handler reconstructs IosPeripheral wrappers via PeripheralRegistry
 * 3. Restores persisted observation keys from the Keychain
 * 4. Triggers reconnection and observation re-subscription
 */
@OptIn(ExperimentalUuidApi::class)
internal object StateRestorationHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val persistence = ObservationPersistence()

    /**
     * Start listening for state restoration events.
     * Called once when state restoration is enabled.
     */
    internal fun start() {
        scope.launch {
            CentralManagerProvider.scanDelegate.restoredPeripherals.collect { peripherals ->
                handleRestoredPeripherals(peripherals)
            }
        }
    }

    private fun handleRestoredPeripherals(cbPeripherals: List<CBPeripheral>) {
        logEvent(BleLogEvent.ServerLifecycle("StateRestoration: restoring ${cbPeripherals.size} peripheral(s)"))

        val savedObservations = try {
            persistence.restore()
        } catch (e: Exception) {
            logEvent(BleLogEvent.Error(null, "StateRestoration: failed to restore observations, clearing", e))
            persistence.clear()
            emptySet()
        }

        for (cbPeripheral in cbPeripherals) {
            val identifier = Identifier(cbPeripheral.identifier.UUIDString)

            val peripheral = PeripheralRegistry.getOrCreate(identifier) {
                IosPeripheral(cbPeripheral)
            }

            if (peripheral is IosPeripheral) {
                scope.launch {
                    try {
                        peripheral.restoreFromStateRestoration(savedObservations)
                    } catch (e: Exception) {
                        logEvent(BleLogEvent.Error(identifier, "StateRestoration: failed to restore", e))
                    }
                }
            }
        }
    }

    /**
     * Persist current observation keys. Called by ObservationManager when observations change.
     */
    internal fun persistObservations(keys: Set<ObservationKey>) {
        if (!CentralManagerProvider.isStateRestorationEnabled) return
        try {
            persistence.save(keys)
        } catch (e: Exception) {
            logEvent(BleLogEvent.Error(null, "StateRestoration: failed to persist observations", e))
        }
    }

    /**
     * Clear persisted observations. Called on Peripheral.close().
     */
    internal fun clearPersistedObservations() {
        try {
            persistence.clear()
        } catch (e: Exception) {
            logEvent(BleLogEvent.Error(null, "StateRestoration: failed to clear observations", e))
        }
    }
}
