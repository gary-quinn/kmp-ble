package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.Variant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Maps BlueZ device properties to kmp-ble [Advertisement] values.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun BlueZDeviceSnapshot.toAdvertisement(): Advertisement {
    val signalStrength = checkNotNull(rssi) { "BlueZDeviceSnapshot.rssi is required for Advertisement mapping" }
    return Advertisement(
        identifier = Identifier(address),
        name = name,
        rssi = signalStrength,
        txPower = txPower,
        isConnectable = isConnectableFromFlags(advertisingFlags),
        serviceUuids = serviceUuids.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() },
        manufacturerData = manufacturerData.mapValues { (_, bytes) -> BleData(bytes) },
        serviceData =
            serviceData
                .mapNotNull { (uuid, bytes) ->
                    runCatching { Uuid.parse(uuid) to BleData(bytes) }.getOrNull()
                }.toMap(),
        timestampNanos = System.nanoTime(),
        isLegacy = true,
        rawAdvertising = null,
    ).also { it.platformContext = dbusPath }
}

internal fun BluetoothDevice.toSnapshot(): BlueZDeviceSnapshot? {
    val address = address ?: return null
    return BlueZDeviceSnapshot(
        dbusPath = dbusPath,
        address = address,
        name = name,
        rssi = rssi?.toInt(),
        txPower = txPower?.toInt(),
        serviceUuids = uuids?.toList().orEmpty(),
        manufacturerData = parseManufacturerData(manufacturerData),
        serviceData = serviceData.orEmpty(),
        advertisingFlags = advertisingFlags,
    )
}

internal fun snapshotFromPropertyMap(
    dbusPath: String,
    properties: Map<String, Any?>,
): BlueZDeviceSnapshot? {
    val address = properties["Address"] as? String ?: return null
    return BlueZDeviceSnapshot(
        dbusPath = dbusPath,
        address = address,
        name = (properties["Name"] as? String) ?: (properties["Alias"] as? String),
        rssi = (properties["RSSI"] as? Number)?.toInt(),
        txPower = (properties["TxPower"] as? Number)?.toInt(),
        serviceUuids = stringList(properties["UUIDs"]),
        manufacturerData = parseManufacturerDataFromProperties(properties["ManufacturerData"]),
        serviceData = parseServiceDataFromProperties(properties["ServiceData"]),
        advertisingFlags = byteArrayFromProperty(properties["AdvertisingFlags"]),
    )
}

internal fun snapshotFromChangedProperties(
    dbusPath: String,
    address: String,
    changed: Map<String, Any?>,
): BlueZDeviceSnapshot =
    BlueZDeviceSnapshot(
        dbusPath = dbusPath,
        address = address,
        name = (changed["Name"] as? String) ?: (changed["Alias"] as? String),
        rssi = (changed["RSSI"] as? Number)?.toInt(),
        txPower = (changed["TxPower"] as? Number)?.toInt(),
        serviceUuids = stringList(changed["UUIDs"]),
        manufacturerData = parseManufacturerDataFromProperties(changed["ManufacturerData"]),
        serviceData = parseServiceDataFromProperties(changed["ServiceData"]),
        advertisingFlags = byteArrayFromProperty(changed["AdvertisingFlags"]),
    )

private fun isConnectableFromFlags(flags: ByteArray?): Boolean {
    if (flags == null || flags.isEmpty()) {
        return false
    }
    val flagByte = flags[0].toInt() and 0xFF
    // Flags AD: general/limited discoverable usually implies a connectable undirected event.
    val discoverable = (flagByte and 0x03) != 0
    return discoverable || (flagByte and 0x04) != 0
}

private fun parseManufacturerData(raw: Map<UInt16, ByteArray>?): Map<Int, ByteArray> {
    if (raw == null) return emptyMap()
    return raw.mapKeys { (key, _) -> key.toInt() }
}

@Suppress("UNCHECKED_CAST")
private fun parseManufacturerDataFromProperties(value: Any?): Map<Int, ByteArray> {
    val raw = value as? Map<*, *> ?: return emptyMap()
    return raw
        .mapNotNull { (key, payload) ->
            val companyId =
                when (key) {
                    is UInt16 -> key.toInt()
                    is Number -> key.toInt()
                    else -> return@mapNotNull null
                }
            val bytes =
                when (payload) {
                    is ByteArray -> payload
                    is List<*> -> payload.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
                    else -> return@mapNotNull null
                }
            companyId to bytes
        }.toMap()
}

@Suppress("UNCHECKED_CAST")
private fun parseServiceDataFromProperties(value: Any?): Map<String, ByteArray> {
    val raw = value as? Map<*, *> ?: return emptyMap()
    return raw
        .mapNotNull { (key, payload) ->
            val uuid = key as? String ?: return@mapNotNull null
            val bytes =
                when (payload) {
                    is ByteArray -> payload
                    is List<*> -> payload.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
                    else -> return@mapNotNull null
                }
            uuid to bytes
        }.toMap()
}

@Suppress("UNCHECKED_CAST")
private fun stringList(value: Any?): List<String> {
    val list = value as? List<*> ?: return emptyList()
    return list.mapNotNull { it as? String }
}

@Suppress("UNCHECKED_CAST")
private fun byteArrayFromProperty(value: Any?): ByteArray? =
    when (value) {
        is ByteArray -> value
        is List<*> -> value.mapNotNull { (it as? Number)?.toByte() }.toByteArray().takeIf { it.isNotEmpty() }
        else -> null
    }

internal fun unwrapVariantMap(raw: Map<String, Variant<*>>): Map<String, Any?> =
    raw.mapValues { (_, variant) -> variant.value }
