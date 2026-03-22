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

        while (pos < data.size) {
            val end = min(pos + packetSize, data.size)
            transport.sendData(data.copyOfRange(pos, end))
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
