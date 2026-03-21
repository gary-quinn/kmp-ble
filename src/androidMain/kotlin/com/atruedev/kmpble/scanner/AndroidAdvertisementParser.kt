package com.atruedev.kmpble.scanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.Phy
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
        isLegacy = isLegacy,
        primaryPhy = primaryPhy.toPhy(),
        secondaryPhy = secondaryPhy.toPhyOrNull(),
        advertisingSid = advertisingSid.takeIf { it != ScanResult.SID_NOT_PRESENT },
        periodicAdvertisingInterval = periodicAdvertisingInterval.takeIf { it > 0 },
        dataStatus = when (dataStatus) {
            ScanResult.DATA_COMPLETE -> DataStatus.Complete
            else -> DataStatus.Truncated
        },
        platformContext = this@toAdvertisement,
    )
}

private fun Int.toPhy(): Phy = when (this) {
    BluetoothDevice.PHY_LE_2M -> Phy.Le2M
    BluetoothDevice.PHY_LE_CODED -> Phy.LeCoded
    else -> Phy.Le1M
}

private fun Int.toPhyOrNull(): Phy? = when (this) {
    BluetoothDevice.PHY_LE_1M -> Phy.Le1M
    BluetoothDevice.PHY_LE_2M -> Phy.Le2M
    BluetoothDevice.PHY_LE_CODED -> Phy.LeCoded
    else -> null
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
