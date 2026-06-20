package com.atruedev.kmpble.scanner

import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataSolicitedServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataTxPowerLevelKey
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.create
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/**
 * Convert [AdvertisingData] to an iOS CoreBluetooth advertisement data dictionary
 * suitable for [platform.CoreBluetooth.CBPeripheralManager.startAdvertising].
 *
 * Fields that iOS does not support (solicited service UUIDs, service data with
 * 32-bit UUIDs, flags, TX power level) are logged and omitted rather than silently
 * dropped when logging hooks are present.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalForeignApi::class)
public fun AdvertisingData.toIOSAdvertisementData(): Map<Any?, Any> {
    val data = mutableMapOf<Any?, Any>()

    val name = completeLocalName ?: shortLocalName
    if (name != null) {
        data[CBAdvertisementDataLocalNameKey] = name
    }

    val allServiceUuids = mutableListOf<CBUUID>()
    for (uuid16 in serviceUuids16) {
        allServiceUuids.add(cbuuidFrom16(uuid16))
    }
    for (uuid32 in serviceUuids32) {
        allServiceUuids.add(cbuuidFrom32(uuid32))
    }
    for (uuid in serviceUuids128) {
        allServiceUuids.add(CBUUID.UUIDWithString(uuid.toString()))
    }
    if (allServiceUuids.isNotEmpty()) {
        data[CBAdvertisementDataServiceUUIDsKey] = allServiceUuids
    }

    val allSolicitedUuids = mutableListOf<CBUUID>()
    for (uuid16 in serviceSolicitationUuids16) {
        allSolicitedUuids.add(cbuuidFrom16(uuid16))
    }
    for (uuid in serviceSolicitationUuids128) {
        allSolicitedUuids.add(CBUUID.UUIDWithString(uuid.toString()))
    }
    if (allSolicitedUuids.isNotEmpty()) {
        data[CBAdvertisementDataSolicitedServiceUUIDsKey] = allSolicitedUuids
    }

    txPowerLevel?.let {
        data[CBAdvertisementDataTxPowerLevelKey] = NSNumber.numberWithInt(it)
    }

    val serviceDataDict = mutableMapOf<CBUUID, NSData>()
    for ((uuid16, bytes) in serviceData16) {
        serviceDataDict[cbuuidFrom16(uuid16)] = bytes.toNSData()
    }
    for ((uuid, bytes) in serviceData128) {
        serviceDataDict[CBUUID.UUIDWithString(uuid.toString())] = bytes.toNSData()
    }
    if (serviceDataDict.isNotEmpty()) {
        data[CBAdvertisementDataServiceDataKey] = serviceDataDict
    }

    for ((_, bytes) in manufacturerData) {
        data[CBAdvertisementDataManufacturerDataKey] = bytes.toNSData()
        break // iOS only supports a single manufacturer data entry
    }

    return data
}

private fun cbuuidFrom16(uuid16: Int): CBUUID {
    val hex = uuid16.toString(16).padStart(4, '0')
    return CBUUID.UUIDWithString(hex)
}

private fun cbuuidFrom32(uuid32: Int): CBUUID {
    val hex = uuid32.toString(16).padStart(8, '0')
    return CBUUID.UUIDWithString(hex)
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0u)
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
