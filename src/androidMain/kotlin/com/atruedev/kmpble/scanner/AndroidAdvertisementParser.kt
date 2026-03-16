package com.atruedev.kmpble.scanner

import android.bluetooth.le.ScanResult
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.core.util.size

@OptIn(ExperimentalUuidApi::class)
internal fun ScanResult.toAdvertisement(): Advertisement {
    val record = scanRecord
    return Advertisement(
        identifier = Identifier(device.address),
        name = record?.deviceName,
        rssi = rssi,
        txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        isConnectable = isConnectable,
        serviceUuids = record?.serviceUuids?.map { parcelUuid ->
            Uuid.parse(parcelUuid.uuid.toString())
        } ?: emptyList(),
        manufacturerData = parseManufacturerData(record),
        serviceData = parseServiceData(record),
        timestampNanos = timestampNanos,
        platformContext = this@toAdvertisement,
    )
}

/** Wraps ByteArray from ScanRecord — zero-copy (BleData wraps the reference). */
private fun parseManufacturerData(record: android.bluetooth.le.ScanRecord?): Map<Int, BleData> {
    val sparse = record?.manufacturerSpecificData ?: return emptyMap()
    val result = mutableMapOf<Int, BleData>()
    for (i in 0 until sparse.size) {
        result[sparse.keyAt(i)] = BleData(sparse.valueAt(i))
    }
    return result
}

@OptIn(ExperimentalUuidApi::class)
private fun parseServiceData(record: android.bluetooth.le.ScanRecord?): Map<Uuid, BleData> {
    val raw = record?.serviceData ?: return emptyMap()
    return raw.map { (parcelUuid, data) ->
        Uuid.parse(parcelUuid.uuid.toString()) to BleData(data)
    }.toMap()
}
