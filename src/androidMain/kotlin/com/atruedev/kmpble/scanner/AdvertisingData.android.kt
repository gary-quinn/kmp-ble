package com.atruedev.kmpble.scanner

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import kotlin.uuid.toJavaUuid

/**
 * Convert [AdvertisingData] to Android's [AdvertiseData] for use with
 * [android.bluetooth.le.BluetoothLeAdvertiser].
 *
 * Flags and TX power are handled by Android's [AdvertiseSettings], not
 * [AdvertiseData], so they are ignored in this conversion.
 */
public fun AdvertisingData.toAndroidAdvertiseData(): AdvertiseData {
    val builder = AdvertiseData.Builder()
    val name = completeLocalName ?: shortLocalName
    if (name != null) {
        builder.setIncludeDeviceName(true)
    }
    for (uuid16 in serviceUuids16) {
        builder.addServiceUuid(parcelUuidFrom16(uuid16))
    }
    for (uuid32 in serviceUuids32) {
        builder.addServiceUuid(parcelUuidFrom32(uuid32))
    }
    for (uuid in serviceUuids128) {
        builder.addServiceUuid(ParcelUuid(uuid.toJavaUuid()))
    }
    for (uuid16 in serviceSolicitationUuids16) {
        builder.addServiceSolicitationUuid(parcelUuidFrom16(uuid16))
    }
    for (uuid in serviceSolicitationUuids128) {
        builder.addServiceSolicitationUuid(ParcelUuid(uuid.toJavaUuid()))
    }
    for ((companyId, data) in manufacturerData) {
        builder.addManufacturerData(companyId, data)
    }
    for ((uuid16, data) in serviceData16) {
        builder.addServiceData(parcelUuidFrom16(uuid16), data)
    }
    for ((uuid32, data) in serviceData32) {
        builder.addServiceData(parcelUuidFrom32(uuid32), data)
    }
    for ((uuid, data) in serviceData128) {
        builder.addServiceData(ParcelUuid(uuid.toJavaUuid()), data)
    }
    return builder.build()
}

private fun parcelUuidFrom16(uuid16: Int): ParcelUuid {
    val uuid = java.util.UUID(0x00001800L shl 32 or (uuid16.toLong() and 0xFFFFL), -0x7FFF_BFFF_8000_0000L)
    return ParcelUuid(uuid)
}

private fun parcelUuidFrom32(uuid32: Int): ParcelUuid {
    val uuid = java.util.UUID(uuid32.toLong() shl 32, -0x7FFF_BFFF_8000_0000L)
    return ParcelUuid(uuid)
}
