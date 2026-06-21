package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.Phy
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Builder DSL for constructing [Advertisement] scan records.
 *
 * Provides a type-safe, discoverable way to construct advertising data
 * for testing, simulation, and ad-hoc construction. Every property of
 * [Advertisement] maps to a corresponding builder method.
 *
 * ## Usage
 * ```kotlin
 * val ad = AdvertisingDataBuilder {
 *     name("HeartSensor")
 *     rssi(-55)
 *     serviceUuids("180d")
 *     manufacturerData(0x004C, BleData(myBytes))
 * }.build()
 * ```
 *
 * ## Defaults
 * - identifier: auto-generated "ad-NNN"
 * - rssi: -60
 * - isConnectable: true
 * - isLegacy: true
 * - primaryPhy: Phy.Le1M
 * - dataStatus: DataStatus.Complete
 */
@OptIn(ExperimentalUuidApi::class)
public class AdvertisingDataBuilder {
    private var identifier: Identifier = Identifier("ad-${counter++}")
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
    private var timestampNanos: Long = 0L
    private var rawAdvertising: RawAdvertising? = null

    public fun identifier(value: String) {
        identifier = Identifier(value)
    }

    public fun identifier(value: Identifier) {
        identifier = value
    }

    public fun name(value: String?) {
        name = value
    }

    public fun rssi(value: Int) {
        rssi = value
    }

    public fun txPower(value: Int?) {
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

    public fun secondaryPhy(value: Phy?) {
        secondaryPhy = value
    }

    public fun advertisingSid(value: Int?) {
        advertisingSid = value
    }

    public fun periodicAdvertisingInterval(value: Int?) {
        periodicAdvertisingInterval = value
    }

    public fun dataStatus(value: DataStatus) {
        dataStatus = value
    }

    public fun timestampNanos(value: Long) {
        timestampNanos = value
    }

    public fun rawAdvertising(value: RawAdvertising?) {
        rawAdvertising = value
    }

    public fun serviceUuids(vararg uuids: String) {
        serviceUuids = uuids.map { uuidFrom(it) }
    }

    public fun serviceUuids(vararg uuids: Uuid) {
        serviceUuids = uuids.toList()
    }

    /** Add manufacturer data for a given company ID. Accumulates - call multiple times for multiple companies. */
    public fun manufacturerData(
        companyId: Int,
        data: BleData,
    ) {
        manufacturerData = manufacturerData + (companyId to data)
    }

    /** Add service data for a given UUID. Accumulates - call multiple times for multiple services. */
    public fun serviceData(
        uuid: String,
        data: BleData,
    ) {
        serviceData = serviceData + (uuidFrom(uuid) to data)
    }

    /** Add service data for a given UUID. */
    public fun serviceData(
        uuid: Uuid,
        data: BleData,
    ) {
        serviceData = serviceData + (uuid to data)
    }

    public fun build(): Advertisement =
        Advertisement(
            identifier = identifier,
            name = name,
            rssi = rssi,
            txPower = txPower,
            isConnectable = isConnectable,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            serviceData = serviceData,
            timestampNanos = timestampNanos,
            isLegacy = isLegacy,
            primaryPhy = primaryPhy,
            secondaryPhy = secondaryPhy,
            advertisingSid = advertisingSid,
            periodicAdvertisingInterval = periodicAdvertisingInterval,
            dataStatus = dataStatus,
            rawAdvertising = rawAdvertising,
        )

    public companion object {
        private var counter = 0

        /** DSL entry point: `AdvertisingDataBuilder { name("...") }.build()` */
        public operator fun invoke(block: AdvertisingDataBuilder.() -> Unit): AdvertisingDataBuilder =
            AdvertisingDataBuilder().apply(block)
    }
}

/** Convenience function to build an [Advertisement] with the DSL. */
@OptIn(ExperimentalUuidApi::class)
public fun buildAdvertisement(block: AdvertisingDataBuilder.() -> Unit): Advertisement =
    AdvertisingDataBuilder().apply(block).build()
