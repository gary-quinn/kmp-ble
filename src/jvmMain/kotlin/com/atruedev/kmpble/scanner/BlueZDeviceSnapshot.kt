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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlueZDeviceSnapshot) return false
        return dbusPath == other.dbusPath &&
            address == other.address &&
            name == other.name &&
            rssi == other.rssi &&
            txPower == other.txPower &&
            serviceUuids == other.serviceUuids &&
            mapsOfByteArraysEqual(manufacturerData, other.manufacturerData) &&
            mapsOfStringByteArraysEqual(serviceData, other.serviceData) &&
            byteArraysEqual(advertisingFlags, other.advertisingFlags)
    }

    override fun hashCode(): Int {
        var result = dbusPath.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (rssi ?: 0)
        result = 31 * result + (txPower ?: 0)
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + mapsOfByteArraysHash(manufacturerData)
        result = 31 * result + mapsOfStringByteArraysHash(serviceData)
        result = 31 * result + (advertisingFlags?.contentHashCode() ?: 0)
        return result
    }
}

private fun byteArraysEqual(
    left: ByteArray?,
    right: ByteArray?,
): Boolean {
    if (left === right) return true
    if (left == null || right == null) return false
    return left.contentEquals(right)
}

private fun mapsOfByteArraysEqual(
    left: Map<Int, ByteArray>,
    right: Map<Int, ByteArray>,
): Boolean {
    if (left.size != right.size) return false
    return left.all { (key, value) -> right[key]?.contentEquals(value) == true }
}

private fun mapsOfStringByteArraysEqual(
    left: Map<String, ByteArray>,
    right: Map<String, ByteArray>,
): Boolean {
    if (left.size != right.size) return false
    return left.all { (key, value) -> right[key]?.contentEquals(value) == true }
}

private fun mapsOfByteArraysHash(map: Map<Int, ByteArray>): Int =
    map.entries.fold(0) { acc, (key, value) -> 31 * acc + key.hashCode() + value.contentHashCode() }

private fun mapsOfStringByteArraysHash(map: Map<String, ByteArray>): Int =
    map.entries.fold(0) { acc, (key, value) -> 31 * acc + key.hashCode() + value.contentHashCode() }
