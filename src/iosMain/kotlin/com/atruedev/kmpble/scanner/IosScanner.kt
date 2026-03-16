package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.scanner.internal.applyEmissionPolicy
import com.atruedev.kmpble.scanner.internal.matchesFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBUUID
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi

public class IosScanner(
    configure: ScannerConfig.() -> Unit = {},
) : Scanner {

    private val config: ScannerConfig = ScannerConfig().apply(configure)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sharedScanFlow: Flow<Advertisement> = createRawScanFlow()
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    override val advertisements: Flow<Advertisement> = run {
        var flow: Flow<Advertisement> = sharedScanFlow
            .filter { it.matchesFilters(config.filterGroups) }
            .applyEmissionPolicy(config.emission)

        val timeout = config.timeout
        if (timeout != null) {
            val mark = TimeSource.Monotonic.markNow()
            flow = flow.takeWhile { mark.elapsedNow() < timeout }
        }

        flow
    }

    override fun close() {
        scope.cancel()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createRawScanFlow(): Flow<Advertisement> = callbackFlow {
        val manager = CentralManagerProvider.manager
        val delegate = CentralManagerProvider.scanDelegate

        // Wait for CBCentralManager to reach poweredOn before scanning.
        // Without this, scanForPeripheralsWithServices is silently ignored
        // and CoreBluetooth logs "API MISUSE: can only accept this command
        // while in the powered on state".
        CentralManagerProvider.adapterStateFlow.first { it == BluetoothAdapterState.On }

        val serviceUuids = config.filterGroups.flatMap { andGroup ->
            andGroup.filterIsInstance<ScanPredicate.ServiceUuid>()
                .map { CBUUID.UUIDWithString(it.uuid.toString()) }
        }.distinct().ifEmpty { null }

        val collectJob = this@callbackFlow.launch {
            delegate.scanResults.collect { rawResult ->
                trySend(rawResult.toAdvertisement())
            }
        }

        manager.scanForPeripheralsWithServices(
            serviceUUIDs = serviceUuids,
            options = mapOf<Any?, Any?>(
                CBCentralManagerScanOptionAllowDuplicatesKey to true,
            ),
        )

        awaitClose {
            manager.stopScan()
            collectJob.cancel()
        }
    }
}
