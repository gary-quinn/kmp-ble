package com.atruedev.kmpble.scanner

/**
 * Normalized BlueZ [org.bluez.Device1] properties used to build an [Advertisement].
 *
 * Kept separate from D-Bus wrappers so mapping logic is unit-testable without hardware.
 */
internal data class BlueZDeviceSnapshot(
    val dbusPath: String,
    val address: String,
    val name: String?,
    val rssi: Int?,
    val txPower: Int?,
    val serviceUuids: List<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<String, ByteArray>,
    val advertisingFlags: ByteArray?,
) {
    fun merge(changed: BlueZDeviceSnapshot): BlueZDeviceSnapshot =
        copy(
            name = changed.name ?: name,
            rssi = changed.rssi ?: rssi,
            txPower = changed.txPower ?: txPower,
            serviceUuids = changed.serviceUuids.ifEmpty { serviceUuids },
            manufacturerData = if (changed.manufacturerData.isEmpty()) manufacturerData else changed.manufacturerData,
            serviceData = if (changed.serviceData.isEmpty()) serviceData else changed.serviceData,
            advertisingFlags = changed.advertisingFlags ?: advertisingFlags,
        )
}
