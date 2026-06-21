package com.atruedev.kmpble.testing

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.AdvertisingDataBuilder
import com.atruedev.kmpble.scanner.DataStatus
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanFailedException
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test scanner that emits pre-configured advertisements without real BLE hardware.
 *
 * ```kotlin
 * val scanner = FakeScanner {
 *     advertisement {
 *         name("HeartSensor")
 *         rssi(-55)
 *         serviceUuids("180d")
 *     }
 *     advertisement {
 *         name("TempSensor")
 *         rssi(-70)
 *         serviceUuids("1809")
 *     }
 * }
 *
 * scanner.scanEvents.collect { event ->
 *     when (event) {
 *         is ScanEvent.Found -> handleAd(event.advertisement)
 *         is ScanEvent.Failed -> handleError(event.error)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
public class FakeScanner internal constructor(
    private val fakeAdvertisements: List<Advertisement>,
) : Scanner {
    private val dynamicEvents = MutableSharedFlow<ScanEvent>(replay = 2, extraBufferCapacity = 64)

    override val scanEvents: Flow<ScanEvent> =
        flow {
            for (ad in fakeAdvertisements) {
                emit(ScanEvent.Found(ad))
            }
            dynamicEvents.collect { emit(it) }
        }

    /** Emit an advertisement dynamically after construction. */
    public fun emit(advertisement: Advertisement) {
        dynamicEvents.tryEmit(ScanEvent.Found(advertisement))
    }

    /** Emit a scan failure for testing error handling paths. */
    public fun emitScanFailed(errorCode: Int) {
        dynamicEvents.tryEmit(ScanEvent.Failed(ScanFailedException(errorCode)))
    }

    override fun close() {
        // No resources to release
    }
}

@OptIn(ExperimentalUuidApi::class)
public class FakeScannerBuilder {
    internal val advertisements = mutableListOf<Advertisement>()

    public fun advertisement(block: FakeAdvertisementBuilder.() -> Unit) {
        advertisements += FakeAdvertisementBuilder().apply(block).build()
    }

    internal fun build(): FakeScanner = FakeScanner(advertisements.toList())
}

@OptIn(ExperimentalUuidApi::class)
public class FakeAdvertisementBuilder {
    private val delegate = AdvertisingDataBuilder()

    public fun identifier(value: String) {
        delegate.identifier(value)
    }

    public fun name(value: String) {
        delegate.name(value)
    }

    public fun rssi(value: Int) {
        delegate.rssi(value)
    }

    public fun txPower(value: Int) {
        delegate.txPower(value)
    }

    public fun isConnectable(value: Boolean) {
        delegate.isConnectable(value)
    }

    public fun isLegacy(value: Boolean) {
        delegate.isLegacy(value)
    }

    public fun primaryPhy(value: Phy) {
        delegate.primaryPhy(value)
    }

    public fun secondaryPhy(value: Phy) {
        delegate.secondaryPhy(value)
    }

    public fun advertisingSid(value: Int) {
        delegate.advertisingSid(value)
    }

    public fun periodicAdvertisingInterval(value: Int) {
        delegate.periodicAdvertisingInterval(value)
    }

    public fun dataStatus(value: DataStatus) {
        delegate.dataStatus(value)
    }

    public fun serviceUuids(vararg uuids: String) {
        delegate.serviceUuids(*uuids)
    }

    public fun serviceUuids(vararg uuids: Uuid) {
        delegate.serviceUuids(*uuids)
    }

    public fun manufacturerData(
        companyId: Int,
        data: BleData,
    ) {
        delegate.manufacturerData(companyId, data)
    }

    public fun serviceData(
        uuid: String,
        data: BleData,
    ) {
        delegate.serviceData(uuid, data)
    }

    internal fun build(): Advertisement = delegate.build()
}

@OptIn(ExperimentalUuidApi::class)
public fun FakeScanner(block: FakeScannerBuilder.() -> Unit): FakeScanner = FakeScannerBuilder().apply(block).build()
