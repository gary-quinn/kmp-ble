package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.scanner.internal.toScanEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBUUID
import kotlin.uuid.ExperimentalUuidApi

public class IosScanner(
    configure: ScannerConfig.() -> Unit = {},
) : Scanner {
    private val config: ScannerConfig = ScannerConfig().apply(configure)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val scanEvents: Flow<ScanEvent> = createRawScanFlow().toScanEvents(config, scope)

    override fun close() {
        scope.cancel()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createRawScanFlow(): Flow<Advertisement> =
        callbackFlow {
            val manager = CentralManagerProvider.manager
            val delegate = CentralManagerProvider.scanDelegate

            CentralManagerProvider.adapterStateFlow.first { it == BluetoothAdapterState.On }

            val serviceUuids = buildServiceUuidList()

            // Emit already-connected peripherals that iOS may have auto-connected
            // in the background (bonded devices). These peripherals have stopped
            // advertising, so scanForPeripheralsWithServices alone would miss them.
            //
            // Track retrieved identifiers so the first scan result for a
            // retrieved peripheral is skipped (avoid double-emitting the same
            // peripheral -- once with RSSI=0 from retrieve, once with real RSSI
            // from scan).
            val retrievedIds = mutableSetOf<String>()
            emitRetrievedPeripherals(manager, serviceUuids, retrievedIds) { ad ->
                trySend(ad)
            }

            val collectJob =
                this@callbackFlow.launch {
                    delegate.scanResults.collect { rawResult ->
                        val id = rawResult.peripheral.identifier.UUIDString
                        if (id !in retrievedIds) {
                            trySend(rawResult.toAdvertisement())
                        }
                        // Remove so subsequent RSSI updates still flow through.
                        retrievedIds -= id
                    }
                }

            manager.scanForPeripheralsWithServices(
                serviceUUIDs = null,
                options =
                    mapOf<Any?, Any?>(
                        CBCentralManagerScanOptionAllowDuplicatesKey to true,
                    ),
            )

            awaitClose {
                manager.stopScan()
                collectJob.cancel()
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    private fun buildServiceUuidList(): List<CBUUID>? =
        config.filterGroups
            .flatMap { andGroup ->
                andGroup
                    .filterIsInstance<ScanPredicate.ServiceUuid>()
                    .map { CBUUID.UUIDWithString(it.uuid.toString()) }
            }.distinct()
            .ifEmpty { null }

    internal companion object {
        /**
         * Calls [CBCentralManager.retrieveConnectedPeripheralsWithServices]
         * and emits a synthetic [Advertisement] for each connected peripheral.
         *
         * [retrievedIds] is populated so the scan collector can skip the first
         * scan result for the same peripheral (avoid double emission).
         *
         * Extracted into a static function so tests can verify the
         * CoreBluetooth call without spinning up an [IosScanner].
         */
        internal fun emitRetrievedPeripherals(
            manager: CBCentralManager,
            serviceUuids: List<CBUUID>?,
            retrievedIds: MutableSet<String>,
            emit: (Advertisement) -> Unit,
        ) {
            // retrieveConnectedPeripheralsWithServices expects a non-null
            // List<*> in Kotlin/Native. CoreBluetooth accepts nil for "all
            // services", but K/N bridge crashes (NPE) when converting a null
            // List to NSArray. Until a K/N-safe nil-passing mechanism is
            // available, we skip retrieval when no service filter is set.
            if (serviceUuids == null) return
            @Suppress("UNCHECKED_CAST")
            val connectedPeripherals =
                manager.retrieveConnectedPeripheralsWithServices(serviceUuids as List<*>)
            // Convert CBUUIDs to Uuids so the retrieved Advertisement carries
            // service UUIDs - required for scan filter matching downstream.
            val uuids = serviceUuids.map { uuidFrom(it.UUIDString) }
            for (peripheral in connectedPeripherals) {
                val cbPeripheral = peripheral as? CBPeripheral ?: continue
                val id = cbPeripheral.identifier.UUIDString
                if (retrievedIds.add(id)) {
                    emit(cbPeripheral.toRetrievedAdvertisement(uuids))
                }
            }
        }
    }
}
