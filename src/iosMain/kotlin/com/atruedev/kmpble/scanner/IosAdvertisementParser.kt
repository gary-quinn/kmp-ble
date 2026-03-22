package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.internal.RawScanResult
import platform.CoreBluetooth.CBAdvertisementDataIsConnectable
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataTxPowerLevelKey
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun RawScanResult.toAdvertisement(): Advertisement {
    val data = advertisementData
    return Advertisement(
        identifier = Identifier(peripheral.identifier.UUIDString),
        name = data[CBAdvertisementDataLocalNameKey] as? String ?: peripheral.name,
        rssi = rssi.intValue,
        txPower = (data[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.intValue,
        isConnectable = (data[CBAdvertisementDataIsConnectable] as? NSNumber)?.boolValue ?: true,
        serviceUuids = parseServiceUuids(data),
        manufacturerData = parseManufacturerData(data),
        serviceData = parseServiceData(data),
        timestampNanos = (NSDate().timeIntervalSince1970 * 1_000_000_000).toLong(),
        // CoreBluetooth receives extended advertisements transparently.
        // PHY and advertising set fields are not exposed by the CoreBluetooth API.
        platformContext = peripheral,
    )
}

@OptIn(ExperimentalUuidApi::class)
private fun parseServiceUuids(data: Map<Any?, *>): List<Uuid> {
    val uuids = data[CBAdvertisementDataServiceUUIDsKey] as? List<*> ?: return emptyList()
    return uuids.mapNotNull { uuid ->
        (uuid as? CBUUID)?.let { uuidFrom(it.UUIDString) }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun parseManufacturerData(data: Map<Any?, *>): Map<Int, BleData> {
    val nsData = data[CBAdvertisementDataManufacturerDataKey] as? NSData ?: return emptyMap()
    val bleData = bleDataFromNSData(nsData)
    if (bleData.size < 2) return emptyMap()
    val companyId = (bleData[0].toInt() and 0xFF) or ((bleData[1].toInt() and 0xFF) shl 8)
    val payload = bleData.slice(2, bleData.size)
    return mapOf(companyId to payload)
}

@OptIn(ExperimentalUuidApi::class)
private fun parseServiceData(data: Map<Any?, *>): Map<Uuid, BleData> {
    val raw = data[CBAdvertisementDataServiceDataKey] as? Map<*, *> ?: return emptyMap()
    val result = mutableMapOf<Uuid, BleData>()
    for ((key, value) in raw) {
        val uuid = (key as? CBUUID)?.let { uuidFrom(it.UUIDString) } ?: continue
        val nsData = value as? NSData ?: continue
        result[uuid] = bleDataFromNSData(nsData)
    }
    return result
}
