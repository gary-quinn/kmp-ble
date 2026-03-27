package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.protocol.DfuChecksum
import com.atruedev.kmpble.dfu.protocol.DfuOpcode
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.flow.first
import kotlin.math.min

internal class PacketWriter(
    private val transport: DfuTransport,
    private val prnInterval: Int,
) {

    suspend fun writeData(
        data: ByteArray,
        offset: Int = 0,
        onPacketSent: suspend (bytesSent: Int) -> Unit = {},
    ) {
        val packetSize = transport.mtu
        var pos = offset
        var packetCount = 0

        // Pre-allocate a single reusable buffer for full-sized packets.
        // Only the final (potentially shorter) packet allocates a fresh array.
        val packetBuffer = ByteArray(packetSize)

        while (pos < data.size) {
            val end = min(pos + packetSize, data.size)
            val len = end - pos
            data.copyInto(packetBuffer, 0, pos, end)
            if (len == packetSize) {
                transport.sendData(packetBuffer)
            } else {
                transport.sendData(packetBuffer.copyOfRange(0, len))
            }
            pos = end
            packetCount++

            onPacketSent(pos)

            if (prnInterval > 0 && packetCount % prnInterval == 0 && pos < data.size) {
                transport.notifications.first()
            }
        }
    }
}

internal fun parseChecksumResponse(response: ByteArray): DfuChecksum {
    require(response.size >= 7) { "Checksum response too short: ${response.size} bytes" }
    require(response[0].toInt() == DfuOpcode.RESPONSE) { "Not a response opcode" }
    require(response[1].toInt() == DfuOpcode.CALCULATE_CHECKSUM) { "Not a checksum response" }

    val offset = response.readIntLE(3)
    val crc32 = response.readUIntLE(7)
    return DfuChecksum(offset, crc32)
}
