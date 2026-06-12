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

            // Wait for CBCentralManager to reach poweredOn before scanning.
            // Without this, scanForPeripheralsWithServices is silently ignored
            // and CoreBluetooth logs "API MISUSE: can only accept this command
            // while in the powered on state".
            CentralManagerProvider.adapterStateFlow.first { it == BluetoothAdapterState.On }

            val serviceUuids =
                config.filterGroups
                    .flatMap { andGroup ->
                        andGroup
                            .filterIsInstance<ScanPredicate.ServiceUuid>()
                            .map { CBUUID.UUIDWithString(it.uuid.toString()) }
                    }.distinct()
                    .ifEmpty { null }

            // Emit already-connected peripherals that iOS may have auto-connected
            // in the background (bonded devices). These peripherals have stopped
            // advertising, so scanForPeripheralsWithServices alone would miss them.
            //
            // retrieveConnectedPeripheralsWithServices(serviceUUIDs: List<*>): List<*>
            // accepts a non-null List in K/N. Pass null (silently cast) to retrieve
            // peripherals for all services — CoreBluetooth handles nil correctly.
            val retrievedIds = mutableSetOf<String>()

            @Suppress("UNCHECKED_CAST")
            val connectedPeripherals =
                manager.retrieveConnectedPeripheralsWithServices(
                    (serviceUuids ?: (null as List<*>?)) as List<*>,
                )
            for (peripheral in connectedPeripherals) {
                val cbPeripheral = peripheral as? CBPeripheral ?: continue
                val id = cbPeripheral.identifier.UUIDString
                if (retrievedIds.add(id)) {
                    trySend(cbPeripheral.toRetrievedAdvertisement())
                }
            }

            val collectJob =
                this@callbackFlow.launch {
                    delegate.scanResults.collect { rawResult ->
                        trySend(rawResult.toAdvertisement())
                    }
                }

            manager.scanForPeripheralsWithServices(
                serviceUUIDs = serviceUuids,
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
}
