package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parsed BLE advertisement data from a single scan result.
 *
 * [manufacturerData] and [serviceData] values are [BleData] — zero-copy on iOS
 * (wraps NSData), lightweight on Android (wraps ByteArray). Call [BleData.toByteArray]
 * only when you need a mutable copy for protocol parsing.
 */
@OptIn(ExperimentalUuidApi::class)
public class Advertisement(
    public val identifier: Identifier,
    public val name: String?,
    public val rssi: Int,
    public val txPower: Int?,
    public val isConnectable: Boolean,
    public val serviceUuids: List<Uuid>,
    public val manufacturerData: Map<Int, BleData>,
    public val serviceData: Map<Uuid, BleData>,
    public val timestampNanos: Long,
    internal val platformContext: Any? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Advertisement) return false
        return identifier == other.identifier &&
            name == other.name &&
            rssi == other.rssi &&
            txPower == other.txPower &&
            isConnectable == other.isConnectable &&
            serviceUuids == other.serviceUuids &&
            manufacturerData == other.manufacturerData &&
            serviceData == other.serviceData &&
            timestampNanos == other.timestampNanos
    }

    override fun hashCode(): Int {
        var result = identifier.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + rssi
        result = 31 * result + (txPower ?: 0)
        result = 31 * result + isConnectable.hashCode()
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + manufacturerData.hashCode()
        result = 31 * result + serviceData.hashCode()
        result = 31 * result + timestampNanos.hashCode()
        return result
    }

    override fun toString(): String =
        "Advertisement(identifier=$identifier, name=$name, rssi=$rssi, " +
            "serviceUuids=$serviceUuids)"
}
