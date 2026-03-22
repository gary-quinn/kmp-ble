package com.atruedev.kmpble.profiles.deviceinfo

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class DeviceInformation(
    val manufacturerName: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val hardwareRevision: String? = null,
    val firmwareRevision: String? = null,
    val softwareRevision: String? = null,
    val systemId: SystemId? = null,
    val pnpId: PnpId? = null,
)

public data class SystemId(
    val manufacturerIdentifier: Long,
    val organizationallyUniqueIdentifier: Int,
)

public data class PnpId(
    val vendorIdSource: Int,
    val vendorId: Int,
    val productId: Int,
    val productVersion: Int,
)

public fun parseSystemId(data: ByteArray): SystemId? {
    if (data.size < 8) return null
    val reader = BleByteReader(data)
    // Bluetooth Core Spec Vol 3, Part C, Section 12.3:
    // bytes 0-4: Manufacturer Identifier (40-bit LE)
    // bytes 5-7: OUI (24-bit LE)
    val mfg0 = reader.readUInt8().toLong()
    val mfg1 = reader.readUInt8().toLong()
    val mfg2 = reader.readUInt8().toLong()
    val mfg3 = reader.readUInt8().toLong()
    val mfg4 = reader.readUInt8().toLong()
    val manufacturerIdentifier = mfg0 or (mfg1 shl 8) or (mfg2 shl 16) or (mfg3 shl 24) or (mfg4 shl 32)
    val oui = reader.readUInt8() or (reader.readUInt8() shl 8) or (reader.readUInt8() shl 16)
    return SystemId(
        manufacturerIdentifier = manufacturerIdentifier,
        organizationallyUniqueIdentifier = oui,
    )
}

public fun parsePnpId(data: ByteArray): PnpId? {
    if (data.size < 7) return null
    val reader = BleByteReader(data)
    return PnpId(
        vendorIdSource = reader.readUInt8(),
        vendorId = reader.readUInt16(),
        productId = reader.readUInt16(),
        productVersion = reader.readUInt16(),
    )
}
