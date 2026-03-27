package com.atruedev.kmpble.dfu.protocol.smp

/**
 * SMP (Simple Management Protocol) header — 8 bytes, big-endian.
 *
 * Layout:
 * ```
 * Byte 0:    op (operation)
 * Byte 1:    flags
 * Byte 2-3:  length (payload length after header, big-endian)
 * Byte 4-5:  group (management group, big-endian)
 * Byte 6:    sequence number
 * Byte 7:    command ID
 * ```
 */
internal data class SmpHeader(
    val op: Int,
    val flags: Int,
    val length: Int,
    val group: Int,
    val sequence: Int,
    val commandId: Int,
) {
    fun encode(): ByteArray = byteArrayOf(
        op.toByte(),
        flags.toByte(),
        (length shr 8).toByte(),
        (length and 0xFF).toByte(),
        (group shr 8).toByte(),
        (group and 0xFF).toByte(),
        sequence.toByte(),
        commandId.toByte(),
    )

    companion object {
        const val SIZE = 8

        fun decode(data: ByteArray, offset: Int = 0): SmpHeader {
            require(data.size - offset >= SIZE) { "Not enough bytes for SMP header" }
            return SmpHeader(
                op = data[offset].toInt() and 0xFF,
                flags = data[offset + 1].toInt() and 0xFF,
                length = ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF),
                group = ((data[offset + 4].toInt() and 0xFF) shl 8) or
                    (data[offset + 5].toInt() and 0xFF),
                sequence = data[offset + 6].toInt() and 0xFF,
                commandId = data[offset + 7].toInt() and 0xFF,
            )
        }
    }
}
