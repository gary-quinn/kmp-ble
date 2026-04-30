@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataSolicitedServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataTxPowerLevelKey
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.posix.memcpy

private const val AD_TYPE_LIST_16BIT_SERVICE_UUIDS = 0x03
private const val AD_TYPE_LIST_32BIT_SERVICE_UUIDS = 0x05
private const val AD_TYPE_LIST_128BIT_SERVICE_UUIDS = 0x07
private const val AD_TYPE_COMPLETE_LOCAL_NAME = 0x09
private const val AD_TYPE_TX_POWER_LEVEL = 0x0A
private const val AD_TYPE_LIST_16BIT_SOLICIT_UUIDS = 0x14
private const val AD_TYPE_LIST_128BIT_SOLICIT_UUIDS = 0x15
private const val AD_TYPE_SERVICE_DATA_16BIT = 0x16
private const val AD_TYPE_SERVICE_DATA_32BIT = 0x20
private const val AD_TYPE_SERVICE_DATA_128BIT = 0x21
private const val AD_TYPE_MANUFACTURER_DATA = 0xFF

private const val UUID_BYTES_16 = 2
private const val UUID_BYTES_32 = 4
private const val UUID_BYTES_128 = 16

// AD record length byte is u8 and includes the type byte.
private const val MAX_AD_DATA_BYTES = 0xFE

/**
 * Re-encodes a CoreBluetooth advertisement dictionary into a BLE on-air AD
 * record per Core Spec Vol 3, Part C §11. Faithful for every field the
 * platform surfaces; field order and any unparsed AD types are lost.
 *
 * Pure: no I/O, no shared state, total (skips overflowing or malformed
 * entries instead of throwing).
 */
internal fun reconstructAdvertisingBytes(advertisementData: Map<Any?, *>): BleData {
    val segments =
        buildList {
            encodeLocalName(advertisementData)?.let(::add)
            addAll(encodeServiceUuids(advertisementData))
            addAll(encodeSolicitedServiceUuids(advertisementData))
            encodeTxPower(advertisementData)?.let(::add)
            addAll(encodeServiceData(advertisementData))
            encodeManufacturerData(advertisementData)?.let(::add)
        }
    return BleData(concatenate(segments))
}

private fun encodeLocalName(data: Map<Any?, *>): ByteArray? {
    val name = data[CBAdvertisementDataLocalNameKey] as? String ?: return null
    return encodeTlv(AD_TYPE_COMPLETE_LOCAL_NAME, name.encodeToByteArray())
}

private fun encodeTxPower(data: Map<Any?, *>): ByteArray? {
    val level = data[CBAdvertisementDataTxPowerLevelKey] as? NSNumber ?: return null
    return encodeTlv(AD_TYPE_TX_POWER_LEVEL, byteArrayOf(level.intValue.toByte()))
}

private fun encodeManufacturerData(data: Map<Any?, *>): ByteArray? {
    val payload = data[CBAdvertisementDataManufacturerDataKey] as? NSData ?: return null
    return encodeTlv(AD_TYPE_MANUFACTURER_DATA, payload.copyToByteArray())
}

private fun encodeServiceUuids(data: Map<Any?, *>): List<ByteArray> {
    val uuids = (data[CBAdvertisementDataServiceUUIDsKey] as? List<*>).orEmpty().filterIsInstance<CBUUID>()
    return encodeUuidLists(
        uuids = uuids,
        type16 = AD_TYPE_LIST_16BIT_SERVICE_UUIDS,
        type32 = AD_TYPE_LIST_32BIT_SERVICE_UUIDS,
        type128 = AD_TYPE_LIST_128BIT_SERVICE_UUIDS,
    )
}

private fun encodeSolicitedServiceUuids(data: Map<Any?, *>): List<ByteArray> {
    val uuids = (data[CBAdvertisementDataSolicitedServiceUUIDsKey] as? List<*>).orEmpty().filterIsInstance<CBUUID>()
    return encodeUuidLists(
        uuids = uuids,
        type16 = AD_TYPE_LIST_16BIT_SOLICIT_UUIDS,
        type32 = null,
        type128 = AD_TYPE_LIST_128BIT_SOLICIT_UUIDS,
    )
}

private fun encodeServiceData(data: Map<Any?, *>): List<ByteArray> {
    val raw = data[CBAdvertisementDataServiceDataKey] as? Map<*, *> ?: return emptyList()
    return raw.mapNotNull { (key, value) ->
        val uuid = key as? CBUUID ?: return@mapNotNull null
        val nsData = value as? NSData ?: return@mapNotNull null
        val type =
            when (uuid.data.length.toInt()) {
                UUID_BYTES_16 -> AD_TYPE_SERVICE_DATA_16BIT
                UUID_BYTES_32 -> AD_TYPE_SERVICE_DATA_32BIT
                UUID_BYTES_128 -> AD_TYPE_SERVICE_DATA_128BIT
                else -> return@mapNotNull null
            }
        encodeTlv(type, uuid.toLittleEndianBytes() + nsData.copyToByteArray())
    }
}

private fun encodeUuidLists(
    uuids: List<CBUUID>,
    type16: Int,
    type32: Int?,
    type128: Int,
): List<ByteArray> {
    val grouped = uuids.groupBy { it.data.length.toInt() }

    fun emit(
        type: Int?,
        widthBytes: Int,
    ): ByteArray? {
        if (type == null) return null
        val group = grouped[widthBytes] ?: return null
        if (group.isEmpty()) return null
        return encodeTlv(type, concatenate(group.map(CBUUID::toLittleEndianBytes)))
    }
    return listOfNotNull(
        emit(type16, UUID_BYTES_16),
        emit(type32, UUID_BYTES_32),
        emit(type128, UUID_BYTES_128),
    )
}

private fun encodeTlv(
    type: Int,
    data: ByteArray,
): ByteArray? {
    if (data.size > MAX_AD_DATA_BYTES) return null
    val out = ByteArray(data.size + 2)
    out[0] = (data.size + 1).toByte()
    out[1] = type.toByte()
    data.copyInto(out, destinationOffset = 2)
    return out
}

private fun concatenate(segments: List<ByteArray>): ByteArray {
    if (segments.isEmpty()) return byteArrayOf()
    val out = ByteArray(segments.sumOf { it.size })
    var offset = 0
    for (segment in segments) {
        segment.copyInto(out, offset)
        offset += segment.size
    }
    return out
}

private fun CBUUID.toLittleEndianBytes(): ByteArray = data.copyToByteArray().also { it.reverse() }

private fun NSData.copyToByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return byteArrayOf()
    val out = ByteArray(length)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, this@copyToByteArray.length)
    }
    return out
}
