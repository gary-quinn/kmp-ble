package com.atruedev.kmpble.dfu.protocol.esp

internal object EspOtaOpcode {
    const val OTA_BEGIN: Byte = 0x01
    const val OTA_END: Byte = 0x02
    const val OTA_REBOOT: Byte = 0x03
}

internal object EspOtaResult {
    const val SUCCESS: Byte = 0x00
    const val FAILURE: Byte = 0x01
}

internal fun encodeOtaBegin(firmwareSize: Int): ByteArray = byteArrayOf(
    EspOtaOpcode.OTA_BEGIN,
    (firmwareSize and 0xFF).toByte(),
    ((firmwareSize shr 8) and 0xFF).toByte(),
    ((firmwareSize shr 16) and 0xFF).toByte(),
    ((firmwareSize shr 24) and 0xFF).toByte(),
)

internal fun encodeOtaEnd(hashBytes: ByteArray): ByteArray {
    val packet = ByteArray(1 + hashBytes.size)
    packet[0] = EspOtaOpcode.OTA_END
    hashBytes.copyInto(packet, 1)
    return packet
}

internal fun encodeOtaReboot(): ByteArray = byteArrayOf(EspOtaOpcode.OTA_REBOOT)
