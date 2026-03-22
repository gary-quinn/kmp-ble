package com.atruedev.kmpble.dfu.firmware

public class FirmwarePackage(
    public val initPacket: ByteArray,
    public val firmware: ByteArray,
) {
    public val totalBytes: Long get() = (initPacket.size + firmware.size).toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirmwarePackage) return false
        return initPacket.contentEquals(other.initPacket) && firmware.contentEquals(other.firmware)
    }

    override fun hashCode(): Int = 31 * initPacket.contentHashCode() + firmware.contentHashCode()

    public companion object {
        public fun fromZipBytes(zipData: ByteArray): FirmwarePackage =
            NordicDfuZipParser.parse(zipData)
    }
}
