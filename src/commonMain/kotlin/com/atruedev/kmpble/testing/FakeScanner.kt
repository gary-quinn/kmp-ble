package com.atruedev.kmpble.testing

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.DataStatus
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.uuidFrom
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
 * scanner.advertisements.collect { ad -> ... }
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
public class FakeScanner internal constructor(
    private val fakeAdvertisements: List<Advertisement>,
) : Scanner {
    private val dynamicAdvertisements = MutableSharedFlow<Advertisement>(extraBufferCapacity = 64)

    override val advertisements: Flow<Advertisement> =
        flow {
            for (ad in fakeAdvertisements) {
                emit(ad)
            }
            dynamicAdvertisements.collect { emit(it) }
        }

    /** Emit an advertisement dynamically after construction. */
    public fun emit(advertisement: Advertisement) {
        dynamicAdvertisements.tryEmit(advertisement)
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
    private var identifier: String = "fake-${counter++}"
    private var name: String? = null
    private var rssi: Int = -60
    private var txPower: Int? = null
    private var isConnectable: Boolean = true
    private var serviceUuids: List<Uuid> = emptyList()
    private var manufacturerData: Map<Int, BleData> = emptyMap()
    private var serviceData: Map<Uuid, BleData> = emptyMap()
    private var isLegacy: Boolean = true
    private var primaryPhy: Phy = Phy.Le1M
    private var secondaryPhy: Phy? = null
    private var advertisingSid: Int? = null
    private var periodicAdvertisingInterval: Int? = null
    private var dataStatus: DataStatus = DataStatus.Complete

    public fun identifier(value: String) {
        identifier = value
    }

    public fun name(value: String) {
        name = value
    }

    public fun rssi(value: Int) {
        rssi = value
    }

    public fun txPower(value: Int) {
        txPower = value
    }

    public fun isConnectable(value: Boolean) {
        isConnectable = value
    }

    public fun isLegacy(value: Boolean) {
        isLegacy = value
    }

    public fun primaryPhy(value: Phy) {
        primaryPhy = value
    }

    public fun secondaryPhy(value: Phy) {
        secondaryPhy = value
    }

    public fun advertisingSid(value: Int) {
        advertisingSid = value
    }

    public fun periodicAdvertisingInterval(value: Int) {
        periodicAdvertisingInterval = value
    }

    public fun dataStatus(value: DataStatus) {
        dataStatus = value
    }

    public fun serviceUuids(vararg uuids: String) {
        serviceUuids = uuids.map { uuidFrom(it) }
    }

    public fun serviceUuids(vararg uuids: Uuid) {
        serviceUuids = uuids.toList()
    }

    public fun manufacturerData(
        companyId: Int,
        data: BleData,
    ) {
        manufacturerData = manufacturerData + (companyId to data)
    }

    public fun serviceData(
        uuid: String,
        data: BleData,
    ) {
        serviceData = serviceData + (uuidFrom(uuid) to data)
    }

    internal fun build(): Advertisement =
        Advertisement(
            identifier = Identifier(identifier),
            name = name,
            rssi = rssi,
            txPower = txPower,
            isConnectable = isConnectable,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            serviceData = serviceData,
            timestampNanos = 0L,
            isLegacy = isLegacy,
            primaryPhy = primaryPhy,
            secondaryPhy = secondaryPhy,
            advertisingSid = advertisingSid,
            periodicAdvertisingInterval = periodicAdvertisingInterval,
            dataStatus = dataStatus,
        )

    private companion object {
        private var counter = 0
    }
}

@OptIn(ExperimentalUuidApi::class)
public fun FakeScanner(block: FakeScannerBuilder.() -> Unit): FakeScanner = FakeScannerBuilder().apply(block).build()
