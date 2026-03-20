package com.atruedev.kmpble.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.IosPeripheral
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBPeripheral
import kotlin.concurrent.AtomicInt
import kotlin.uuid.ExperimentalUuidApi

/**
 * Handles iOS Core Bluetooth state restoration.
 *
 * When iOS relaunches the app after termination:
 * 1. willRestoreState provides previously connected CBPeripherals
 * 2. This handler reconstructs IosPeripheral wrappers via PeripheralRegistry
 * 3. Restores persisted observations (keys + backpressure strategy) from NSUserDefaults
 * 4. Triggers reconnection and observation re-subscription
 *
 * Constructed as a class (not object) to allow dependency injection for testing.
 * The default singleton is accessed via [StateRestorationHandler.default].
 */
@OptIn(ExperimentalUuidApi::class)
internal class StateRestorationHandler(
    private val persistence: ObservationPersistence = ObservationPersistence(),
    private val centralManagerProvider: CentralManagerProvider = CentralManagerProvider,
    private val peripheralRegistry: PeripheralRegistry = PeripheralRegistry,
) {

    private var scope: CoroutineScope? = null
    private val started = AtomicInt(0)

    /**
     * Start listening for state restoration events.
     * Called once when state restoration is enabled.
     *
     * Also prunes stale NSUserDefaults keys from peripherals that were
     * never explicitly closed in previous sessions.
     */
    fun start() {
        if (!started.compareAndSet(0, 1)) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        safeLog("prune stale keys") {
            val activeIds = peripheralRegistry.identifiers()
            persistence.pruneStaleEntries(activeIds)
        }

        newScope.launch {
            centralManagerProvider.scanDelegate.restoredPeripherals.collect { peripherals ->
                handleRestoredPeripherals(peripherals)
            }
        }
    }

    /**
     * Cancel the restoration collector scope. Prevents zombie collectors
     * if the host app's BLE session ends and restarts.
     */
    fun stop() {
        scope?.cancel()
        scope = null
        started.value = 0
    }

    private fun handleRestoredPeripherals(cbPeripherals: List<CBPeripheral>) {
        logEvent(BleLogEvent.StateRestoration(null, "restoring ${cbPeripherals.size} peripheral(s)"))

        for (cbPeripheral in cbPeripherals) {
            val identifier = Identifier(cbPeripheral.identifier.UUIDString)

            val savedObservations = try {
                persistence.restore(identifier.value)
            } catch (e: Exception) {
                logEvent(BleLogEvent.Error(identifier, "StateRestoration: failed to restore observations, clearing", e))
                try { persistence.clear(identifier.value) } catch (_: Exception) {}
                emptySet()
            }

            val peripheral = peripheralRegistry.getOrCreate(identifier) {
                IosPeripheral(cbPeripheral)
            }

            if (peripheral is IosPeripheral) {
                scope?.launch {
                    try {
                        peripheral.restoreFromStateRestoration(savedObservations)
                        logEvent(BleLogEvent.StateRestoration(identifier, "restored successfully"))
                    } catch (e: Exception) {
                        logEvent(BleLogEvent.Error(identifier, "StateRestoration: failed to restore", e))
                    }
                }
            }
        }
    }

    /**
     * Persist current observations for a specific peripheral.
     * Called by ObservationManager when observations change.
     */
    fun persistObservations(peripheralId: String, observations: Set<PersistedObservation>) {
        if (!centralManagerProvider.isStateRestorationEnabled) return
        safeLog("persist observations") { persistence.save(peripheralId, observations) }
    }

    /**
     * Clear persisted observations for a specific peripheral. Called on Peripheral.close().
     */
    fun clearPersistedObservations(peripheralId: String) {
        safeLog("clear observations") { persistence.clear(peripheralId) }
    }

    private inline fun safeLog(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logEvent(BleLogEvent.Error(null, "StateRestoration: failed to $operation", e))
        }
    }

    companion object {
        /** Default singleton instance for production use. */
        val default = StateRestorationHandler()
    }
}
