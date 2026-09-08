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
        manufacturerData = parseManufacturerDataAny(manufacturerData as Map<*, *>?),
        serviceData = parseServiceDataAny(serviceData as Map<*, *>?),
        advertisingFlags = byteArrayFromAny(advertisingFlags),
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
    val discoverable = (flagByte and 0x03) != 0
    return discoverable || (flagByte and 0x04) != 0
}

internal fun byteArrayFromAny(value: Any?): ByteArray? {
    val unwrapped = unwrapVariantValue(value) ?: return null
    return when (unwrapped) {
        is ByteArray -> unwrapped
        is List<*> -> listToByteArray(unwrapped)
        is Array<*> -> listToByteArray(unwrapped.toList())
        is Number -> byteArrayOf(unwrapped.toByte())
        else -> null
    }
}

private fun listToByteArray(elements: List<*>): ByteArray? {
    val bytes = elements.mapNotNull { byteFromAny(it) }
    return bytes.toByteArray().takeIf { it.isNotEmpty() }
}

private fun byteFromAny(value: Any?): Byte? {
    val unwrapped = unwrapVariantValue(value) ?: return null
    return when (unwrapped) {
        is Number -> unwrapped.toByte()
        is Byte -> unwrapped
        else -> null
    }
}

private fun unwrapVariantValue(value: Any?): Any? =
    when (value) {
        null -> null
        is Variant<*> -> unwrapVariantValue(value.value)
        else -> value
    }

private fun parseManufacturerDataAny(raw: Map<*, *>?): Map<Int, ByteArray> {
    if (raw == null) return emptyMap()
    return raw
        .mapNotNull { (key, payload) ->
            val companyId =
                when (val unwrappedKey = unwrapVariantValue(key)) {
                    is UInt16 -> unwrappedKey.toInt()
                    is Number -> unwrappedKey.toInt()
                    else -> return@mapNotNull null
                }
            val bytes = byteArrayFromAny(payload) ?: return@mapNotNull null
            companyId to bytes
        }.toMap()
}

private fun parseServiceDataAny(raw: Map<*, *>?): Map<String, ByteArray> {
    if (raw == null) return emptyMap()
    return raw
        .mapNotNull { (key, payload) ->
            val uuid =
                when (val unwrappedKey = unwrapVariantValue(key)) {
                    is String -> unwrappedKey
                    else -> return@mapNotNull null
                }
            val bytes = byteArrayFromAny(payload) ?: return@mapNotNull null
            uuid to bytes
        }.toMap()
}

private fun parseManufacturerDataFromProperties(value: Any?): Map<Int, ByteArray> {
    val raw = unwrapVariantValue(value) as? Map<*, *> ?: return emptyMap()
    return parseManufacturerDataAny(raw)
}

private fun parseServiceDataFromProperties(value: Any?): Map<String, ByteArray> {
    val raw = unwrapVariantValue(value) as? Map<*, *> ?: return emptyMap()
    return parseServiceDataAny(raw)
}

private fun stringList(value: Any?): List<String> {
    val list = unwrapVariantValue(value) as? List<*> ?: return emptyList()
    return list.mapNotNull { unwrapVariantValue(it) as? String }
}

private fun byteArrayFromProperty(value: Any?): ByteArray? = byteArrayFromAny(value)

internal fun unwrapVariantMap(raw: Map<String, Variant<*>>): Map<String, Any?> =
    raw.mapValues { (_, variant) -> variant.value }
