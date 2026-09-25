package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.backend.BleBackend
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.ScanRecord
import com.atruedev.kmpble.backend.ScanRequest
import com.atruedev.kmpble.backend.ScanTransport
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.RawAdvertising
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanPredicate
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScannerConfig
import com.atruedev.kmpble.scanner.internal.toScanEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(KmpBleBackendApi::class)
internal class BackendScanner(
    private val backend: BleBackend,
    configure: ScannerConfig.() -> Unit,
) : Scanner {
    private val config = ScannerConfig().apply(configure)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport: ScanTransport by lazy { backend.createScanTransport() }

    override val scanEvents: Flow<ScanEvent> = rawAdvertisements().toScanEvents(config, scope)

    override fun close() {
        scope.cancel()
    }

    private fun rawAdvertisements(): Flow<Advertisement> {
        val request = ScanRequest(serviceUuids = serviceUuidHint(config.filterGroups), legacyOnly = config.legacyOnly)
        return flow {
            transport
                .scan(request)
                .map { it.toAdvertisement(backend) }
                .collect { emit(it) }
        }.onStart { logEvent(BleLogEvent.ScanStarted(config.filterGroups.size)) }
            .onCompletion { cause -> logEvent(BleLogEvent.ScanStopped(cause?.message ?: "closed")) }
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun serviceUuidHint(filterGroups: List<List<ScanPredicate>>): List<Uuid> {
    if (filterGroups.isEmpty()) return emptyList()
    val perGroup = filterGroups.map { group -> group.filterIsInstance<ScanPredicate.ServiceUuid>().map { it.uuid } }
    if (perGroup.any { it.isEmpty() }) return emptyList()
    return perGroup.flatten().distinct()
}

@OptIn(KmpBleBackendApi::class)
internal fun ScanRecord.toAdvertisement(backend: BleBackend): Advertisement =
    Advertisement(
        identifier = identifier,
        name = name,
        rssi = rssi,
        txPower = txPower,
        isConnectable = isConnectable,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData.mapValues { (_, bytes) -> BleData(bytes) },
        serviceData = serviceData.mapValues { (_, bytes) -> BleData(bytes) },
        timestampNanos = timestampNanos,
        isLegacy = true,
        rawAdvertising = rawAdvertising?.let { RawAdvertising.Reconstructed(BleData(it)) },
    ).also { it.platformContext = BackendAdvertisementContext(backend, handle) }

@OptIn(KmpBleBackendApi::class)
internal class BackendAdvertisementContext(
    val backend: BleBackend,
    val handle: String,
)
