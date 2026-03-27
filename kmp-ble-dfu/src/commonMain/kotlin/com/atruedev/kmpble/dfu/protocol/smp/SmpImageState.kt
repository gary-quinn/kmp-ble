package com.atruedev.kmpble.dfu.protocol.smp

/**
 * Represents the state of a single image slot on a MCUboot device.
 *
 * @property slot slot number (0 = primary/active, 1 = secondary/update)
 * @property version firmware version string (e.g. "1.2.3")
 * @property hash SHA256 hash of the image (32 bytes)
 * @property bootable whether the image is valid and can be booted
 * @property pending whether the image is pending test-boot
 * @property confirmed whether the image has been confirmed as stable
 * @property active whether this slot is currently running
 * @property permanent whether the image is permanently installed
 */
internal data class SmpImageSlot(
    val slot: Int,
    val version: String,
    val hash: ByteArray?,
    val bootable: Boolean,
    val pending: Boolean,
    val confirmed: Boolean,
    val active: Boolean,
    val permanent: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmpImageSlot) return false
        return slot == other.slot && version == other.version && bootable == other.bootable &&
            pending == other.pending && confirmed == other.confirmed && active == other.active &&
            permanent == other.permanent && hash.contentEqualsNullable(other.hash)
    }

    override fun hashCode(): Int = 31 * slot + version.hashCode()

    companion object {
        fun fromCborMap(map: Map<Int, Any>): SmpImageSlot = SmpImageSlot(
            slot = (map[0] as? Long)?.toInt() ?: 0,
            version = map[1] as? String ?: "0.0.0",
            hash = map[2] as? ByteArray,
            bootable = map[3] as? Boolean ?: false,
            pending = map[4] as? Boolean ?: false,
            confirmed = map[5] as? Boolean ?: false,
            active = map[6] as? Boolean ?: false,
            permanent = map[7] as? Boolean ?: false,
        )
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && contentEquals(other)
