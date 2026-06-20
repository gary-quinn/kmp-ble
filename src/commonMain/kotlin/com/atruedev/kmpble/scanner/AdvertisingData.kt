package com.atruedev.kmpble.scanner

import kotlin.uuid.Uuid

/**
 * Structured BLE advertising data suitable for encoding into an AD record
 * per Core Spec Vol 3, Part C, Section 11.
 *
 * Use the [AdvertisingData] builder DSL to construct:
 *
 * ```kotlin
 * val data = AdvertisingData {
 *     flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE, AdvertisingFlags.BR_EDR_NOT_SUPPORTED)
 *     completeLocalName("My Device")
 *     serviceUuid16(0x180D) // Heart Rate
 *     manufacturerData(0x004C, byteArrayOf(0x02, 0x15, ...))
 *     txPowerLevel(-59)
 * }
 * ```
 *
 * Call [encode] to produce the on-air AD record bytes. Pass to platform
 * advertising APIs via extension functions on Android and iOS.
 */
public class AdvertisingData internal constructor(
    /** Advertising flags bitmask (AD Type 0x01). 0 = no flags set. */
    public val flags: Int = 0,
    /** Complete local name (AD Type 0x09). */
    public val completeLocalName: String? = null,
    /** Shortened local name (AD Type 0x08). */
    public val shortLocalName: String? = null,
    /** Complete list of 16-bit Service UUIDs (AD Type 0x03). */
    public val serviceUuids16: List<Int> = emptyList(),
    /** Complete list of 32-bit Service UUIDs (AD Type 0x05). */
    public val serviceUuids32: List<Int> = emptyList(),
    /** Complete list of 128-bit Service UUIDs (AD Type 0x07). */
    public val serviceUuids128: List<Uuid> = emptyList(),
    /** List of 16-bit Service Solicitation UUIDs (AD Type 0x14). */
    public val serviceSolicitationUuids16: List<Int> = emptyList(),
    /** List of 128-bit Service Solicitation UUIDs (AD Type 0x15). */
    public val serviceSolicitationUuids128: List<Uuid> = emptyList(),
    /** Manufacturer specific data keyed by company ID (AD Type 0xFF). */
    public val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    /** TX power level in dBm (AD Type 0x0A). */
    public val txPowerLevel: Int? = null,
    /** Service data keyed by 16-bit UUID (AD Type 0x16). */
    public val serviceData16: Map<Int, ByteArray> = emptyMap(),
    /** Service data keyed by 32-bit UUID (AD Type 0x20). */
    public val serviceData32: Map<Int, ByteArray> = emptyMap(),
    /** Service data keyed by 128-bit UUID (AD Type 0x21). */
    public val serviceData128: Map<Uuid, ByteArray> = emptyMap(),
) {
    /**
     * Encode this advertising data into a BLE AD record byte array
     * per Core Spec Vol 3, Part C, Section 11.
     *
     * Returns empty ByteArray if no AD structures are present.
     */
    public fun encode(): ByteArray {
        val segments = buildList {
            if (flags != 0) add(encodeTlv(AD_TYPE_FLAGS, byteArrayOf(flags.toByte())))
            completeLocalName?.let {
                add(encodeTlv(AD_TYPE_COMPLETE_LOCAL_NAME, it.encodeToByteArray()))
            }
            shortLocalName?.let {
                add(encodeTlv(AD_TYPE_SHORT_LOCAL_NAME, it.encodeToByteArray()))
            }
            if (serviceUuids16.isNotEmpty()) {
                add(encodeTlv(AD_TYPE_LIST_16BIT_SERVICE_UUIDS, encodeUuids16(serviceUuids16)))
            }
            if (serviceUuids32.isNotEmpty()) {
                add(encodeTlv(AD_TYPE_LIST_32BIT_SERVICE_UUIDS, encodeUuids32(serviceUuids32)))
            }
            if (serviceUuids128.isNotEmpty()) {
                add(encodeTlv(AD_TYPE_LIST_128BIT_SERVICE_UUIDS, encodeUuids128(serviceUuids128)))
            }
            if (serviceSolicitationUuids16.isNotEmpty()) {
                add(encodeTlv(AD_TYPE_LIST_16BIT_SOLICIT_UUIDS, encodeUuids16(serviceSolicitationUuids16)))
            }
            if (serviceSolicitationUuids128.isNotEmpty()) {
                add(encodeTlv(AD_TYPE_LIST_128BIT_SOLICIT_UUIDS, encodeUuids128(serviceSolicitationUuids128)))
            }
            txPowerLevel?.let {
                add(encodeTlv(AD_TYPE_TX_POWER_LEVEL, byteArrayOf(it.toByte())))
            }
            for ((uuid16, data) in serviceData16) {
                add(encodeTlv(AD_TYPE_SERVICE_DATA_16BIT, encodeUuid16(uuid16) + data))
            }
            for ((uuid32, data) in serviceData32) {
                add(encodeTlv(AD_TYPE_SERVICE_DATA_32BIT, encodeUuid32(uuid32) + data))
            }
            for ((uuid, data) in serviceData128) {
                add(encodeTlv(AD_TYPE_SERVICE_DATA_128BIT, encodeUuid128(uuid) + data))
            }
            for ((companyId, data) in manufacturerData) {
                add(encodeTlv(AD_TYPE_MANUFACTURER_DATA, encodeUuid16(companyId) + data))
            }
        }
        if (segments.isEmpty()) return byteArrayOf()
        val totalSize = segments.sumOf { it.size }
        val out = ByteArray(totalSize)
        var offset = 0
        for (segment in segments) {
            segment.copyInto(out, offset)
            offset += segment.size
        }
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdvertisingData) return false
        return flags == other.flags &&
            completeLocalName == other.completeLocalName &&
            shortLocalName == other.shortLocalName &&
            serviceUuids16 == other.serviceUuids16 &&
            serviceUuids32 == other.serviceUuids32 &&
            serviceUuids128 == other.serviceUuids128 &&
            serviceSolicitationUuids16 == other.serviceSolicitationUuids16 &&
            serviceSolicitationUuids128 == other.serviceSolicitationUuids128 &&
            manufacturerData.entries.all { (k, v) ->
                other.manufacturerData[k]?.contentEquals(v) == true
            } &&
            other.manufacturerData.size == manufacturerData.size &&
            txPowerLevel == other.txPowerLevel &&
            serviceData16.entries.all { (k, v) ->
                other.serviceData16[k]?.contentEquals(v) == true
            } &&
            other.serviceData16.size == serviceData16.size &&
            serviceData32.entries.all { (k, v) ->
                other.serviceData32[k]?.contentEquals(v) == true
            } &&
            other.serviceData32.size == serviceData32.size &&
            serviceData128.entries.all { (k, v) ->
                other.serviceData128[k]?.contentEquals(v) == true
            } &&
            other.serviceData128.size == serviceData128.size
    }

    override fun hashCode(): Int {
        var result = flags
        result = 31 * result + (completeLocalName?.hashCode() ?: 0)
        result = 31 * result + (shortLocalName?.hashCode() ?: 0)
        result = 31 * result + serviceUuids16.hashCode()
        result = 31 * result + serviceUuids32.hashCode()
        result = 31 * result + serviceUuids128.hashCode()
        result = 31 * result + serviceSolicitationUuids16.hashCode()
        result = 31 * result + serviceSolicitationUuids128.hashCode()
        for ((k, v) in manufacturerData) {
            result = 31 * result + k.hashCode() + v.contentHashCode()
        }
        result = 31 * result + (txPowerLevel ?: 0)
        for ((k, v) in serviceData16) {
            result = 31 * result + k.hashCode() + v.contentHashCode()
        }
        for ((k, v) in serviceData32) {
            result = 31 * result + k.hashCode() + v.contentHashCode()
        }
        for ((k, v) in serviceData128) {
            result = 31 * result + k.hashCode() + v.contentHashCode()
        }
        return result
    }

    override fun toString(): String =
        "AdvertisingData(flags=0x${flags.toString(16)}, " +
            "completeLocalName=$completeLocalName, " +
            "serviceUuids16=$serviceUuids16, " +
            "txPowerLevel=$txPowerLevel, " +
            "manufacturerData=${manufacturerData.keys})"

    internal companion object {
        internal const val AD_TYPE_FLAGS = 0x01
        internal const val AD_TYPE_LIST_16BIT_SERVICE_UUIDS = 0x03
        internal const val AD_TYPE_LIST_32BIT_SERVICE_UUIDS = 0x05
        internal const val AD_TYPE_LIST_128BIT_SERVICE_UUIDS = 0x07
        internal const val AD_TYPE_SHORT_LOCAL_NAME = 0x08
        internal const val AD_TYPE_COMPLETE_LOCAL_NAME = 0x09
        internal const val AD_TYPE_TX_POWER_LEVEL = 0x0A
        internal const val AD_TYPE_LIST_16BIT_SOLICIT_UUIDS = 0x14
        internal const val AD_TYPE_LIST_128BIT_SOLICIT_UUIDS = 0x15
        internal const val AD_TYPE_SERVICE_DATA_16BIT = 0x16
        internal const val AD_TYPE_SERVICE_DATA_32BIT = 0x20
        internal const val AD_TYPE_SERVICE_DATA_128BIT = 0x21
        internal const val AD_TYPE_MANUFACTURER_DATA = 0xFF.toByte().toInt() and 0xFF

        internal fun encodeTlv(type: Int, data: ByteArray): ByteArray {
            val out = ByteArray(data.size + 2)
            out[0] = (data.size + 1).toByte()
            out[1] = type.toByte()
            data.copyInto(out, destinationOffset = 2)
            return out
        }

        internal fun encodeUuid16(value: Int): ByteArray =
            byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

        internal fun encodeUuid32(value: Int): ByteArray =
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            )

        internal fun encodeUuid128(uuid: Uuid): ByteArray {
            val bytes = ByteArray(16)
            val hex = uuid.toString().filter { it != '-' }
            for (i in 0 until 16) {
                bytes[15 - i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return bytes
        }

        internal fun encodeUuids16(values: List<Int>): ByteArray {
            val out = ByteArray(values.size * 2)
            var offset = 0
            for (value in values) {
                val encoded = encodeUuid16(value)
                encoded.copyInto(out, offset)
                offset += 2
            }
            return out
        }

        internal fun encodeUuids32(values: List<Int>): ByteArray {
            val out = ByteArray(values.size * 4)
            var offset = 0
            for (value in values) {
                val encoded = encodeUuid32(value)
                encoded.copyInto(out, offset)
                offset += 4
            }
            return out
        }

        internal fun encodeUuids128(values: List<Uuid>): ByteArray {
            val out = ByteArray(values.size * 16)
            var offset = 0
            for (value in values) {
                val encoded = encodeUuid128(value)
                encoded.copyInto(out, offset)
                offset += 16
            }
            return out
        }
    }
}

/**
 * Builder for constructing [AdvertisingData] via a type-safe DSL.
 *
 * ```kotlin
 * AdvertisingData {
 *     flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE, AdvertisingFlags.BR_EDR_NOT_SUPPORTED)
 *     completeLocalName("My Device")
 *     serviceUuid16(0x180D)
 *     manufacturerData(0x004C, byteArrayOf(0x02, 0x15, 0x01))
 *     txPowerLevel(-59)
 * }
 * ```
 */
public class AdvertisingDataBuilder internal constructor() {
    internal var flags: Int = 0
    internal var completeLocalName: String? = null
    internal var shortLocalName: String? = null
    internal val serviceUuids16: MutableList<Int> = mutableListOf()
    internal val serviceUuids32: MutableList<Int> = mutableListOf()
    internal val serviceUuids128: MutableList<Uuid> = mutableListOf()
    internal val serviceSolicitationUuids16: MutableList<Int> = mutableListOf()
    internal val serviceSolicitationUuids128: MutableList<Uuid> = mutableListOf()
    internal val manufacturerData: MutableMap<Int, ByteArray> = mutableMapOf()
    internal var txPowerLevel: Int? = null
    internal val serviceData16: MutableMap<Int, ByteArray> = mutableMapOf()
    internal val serviceData32: MutableMap<Int, ByteArray> = mutableMapOf()
    internal val serviceData128: MutableMap<Uuid, ByteArray> = mutableMapOf()

    /**
     * Set advertising flags. Multiple flags are OR'd together.
     * Maps to AD Type 0x01.
     */
    public fun flags(vararg flag: AdvertisingFlags) {
        flags = flag.fold(0) { acc, f -> acc or f.mask }
    }

    /**
     * Set the complete local name (AD Type 0x09).
     * Prefer this over [shortLocalName] when the full name fits in 31 bytes.
     */
    public fun completeLocalName(name: String) {
        completeLocalName = name
    }

    /**
     * Set a shortened local name (AD Type 0x08).
     * Use only when the full name exceeds AD payload limits.
     */
    public fun shortLocalName(name: String) {
        shortLocalName = name
    }

    /**
     * Add a 16-bit Service UUID to the complete list (AD Type 0x03).
     */
    public fun serviceUuid16(uuid: Int) {
        serviceUuids16.add(uuid)
    }

    /**
     * Add a 32-bit Service UUID to the complete list (AD Type 0x05).
     */
    public fun serviceUuid32(uuid: Int) {
        serviceUuids32.add(uuid)
    }

    /**
     * Add a 128-bit Service UUID to the complete list (AD Type 0x07).
     */
    public fun serviceUuid128(uuid: Uuid) {
        serviceUuids128.add(uuid)
    }

    /**
     * Add a 16-bit Service Solicitation UUID (AD Type 0x14).
     */
    public fun serviceSolicitationUuid16(uuid: Int) {
        serviceSolicitationUuids16.add(uuid)
    }

    /**
     * Add a 128-bit Service Solicitation UUID (AD Type 0x15).
     */
    public fun serviceSolicitationUuid128(uuid: Uuid) {
        serviceSolicitationUuids128.add(uuid)
    }

    /**
     * Add manufacturer-specific data keyed by company ID (AD Type 0xFF).
     *
     * @param companyId Bluetooth SIG company identifier.
     * @param data Manufacturer-specific payload.
     */
    public fun manufacturerData(companyId: Int, data: ByteArray) {
        manufacturerData[companyId] = data
    }

    /**
     * Set the TX power level in dBm (AD Type 0x0A).
     *
     * @param dBm TX power in dBm, typically a signed value like -59.
     */
    public fun txPowerLevel(dBm: Int) {
        txPowerLevel = dBm
    }

    /**
     * Add service data associated with a 16-bit UUID (AD Type 0x16).
     */
    public fun serviceData16(uuid: Int, data: ByteArray) {
        serviceData16[uuid] = data
    }

    /**
     * Add service data associated with a 32-bit UUID (AD Type 0x20).
     */
    public fun serviceData32(uuid: Int, data: ByteArray) {
        serviceData32[uuid] = data
    }

    /**
     * Add service data associated with a 128-bit UUID (AD Type 0x21).
     */
    public fun serviceData128(uuid: Uuid, data: ByteArray) {
        serviceData128[uuid] = data
    }

    internal fun build(): AdvertisingData =
        AdvertisingData(
            flags = flags,
            completeLocalName = completeLocalName,
            shortLocalName = shortLocalName,
            serviceUuids16 = serviceUuids16.toList(),
            serviceUuids32 = serviceUuids32.toList(),
            serviceUuids128 = serviceUuids128.toList(),
            serviceSolicitationUuids16 = serviceSolicitationUuids16.toList(),
            serviceSolicitationUuids128 = serviceSolicitationUuids128.toList(),
            manufacturerData = manufacturerData.toMap(),
            txPowerLevel = txPowerLevel,
            serviceData16 = serviceData16.toMap(),
            serviceData32 = serviceData32.toMap(),
            serviceData128 = serviceData128.toMap(),
        )
}

/**
 * Construct an [AdvertisingData] using the builder DSL.
 *
 * ```kotlin
 * val data = AdvertisingData {
 *     flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE)
 *     completeLocalName("Heart Rate Sensor")
 *     serviceUuid16(0x180D)
 * }
 * val bytes: ByteArray = data.encode()
 * ```
 */
public fun AdvertisingData(block: AdvertisingDataBuilder.() -> Unit): AdvertisingData =
    AdvertisingDataBuilder().apply(block).build()
