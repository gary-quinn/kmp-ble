package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.protocol.Crc32
import com.atruedev.kmpble.dfu.protocol.DfuChecksum
import com.atruedev.kmpble.dfu.protocol.DfuExtendedError
import com.atruedev.kmpble.dfu.protocol.DfuObjectInfo
import com.atruedev.kmpble.dfu.protocol.DfuOpcode
import com.atruedev.kmpble.dfu.protocol.DfuResultCode
import com.atruedev.kmpble.dfu.transport.DfuTransport

internal class ObjectTransfer(
    private val transport: DfuTransport,
    private val prnInterval: Int,
) {

    suspend fun select(objectType: Int): DfuObjectInfo {
        val command = byteArrayOf(DfuOpcode.SELECT.toByte(), objectType.toByte())
        val response = transport.sendCommand(command)
        validateResponse(response, DfuOpcode.SELECT)

        require(response.size >= 15) { "Select response too short: ${response.size} bytes" }
        return DfuObjectInfo(
            maxSize = response.readIntLE(3),
            offset = response.readIntLE(7),
            crc32 = response.readUIntLE(11),
        )
    }

    suspend fun create(objectType: Int, size: Int) {
        val command = byteArrayOf(DfuOpcode.CREATE.toByte(), objectType.toByte()) +
            size.toLittleEndianBytes()
        val response = transport.sendCommand(command)
        validateResponse(response, DfuOpcode.CREATE)
    }

    suspend fun setPrn(interval: Int) {
        val command = byteArrayOf(DfuOpcode.SET_PRN.toByte()) +
            interval.toShort().toLittleEndianBytes()
        val response = transport.sendCommand(command)
        validateResponse(response, DfuOpcode.SET_PRN)
    }

    suspend fun writeData(
        data: ByteArray,
        offset: Int = 0,
        onPacketSent: suspend (bytesSent: Int) -> Unit = {},
    ) {
        val writer = PacketWriter(transport, prnInterval)
        writer.writeData(data, offset, onPacketSent)
    }

    suspend fun calculateChecksum(): DfuChecksum {
        val command = byteArrayOf(DfuOpcode.CALCULATE_CHECKSUM.toByte())
        val response = transport.sendCommand(command)
        validateResponse(response, DfuOpcode.CALCULATE_CHECKSUM)
        return parseChecksumResponse(response)
    }

    suspend fun execute() {
        val command = byteArrayOf(DfuOpcode.EXECUTE.toByte())
        val response = transport.sendCommand(command)
        validateResponse(response, DfuOpcode.EXECUTE)
    }

    suspend fun transferObject(
        objectType: Int,
        data: ByteArray,
        onProgress: suspend (bytesSent: Int) -> Unit = {},
    ) {
        val info = select(objectType)
        val resumeOffset = resolveResumeOffset(info, data)

        if (resumeOffset == data.size) {
            execute()
            return
        }

        if (resumeOffset == 0) {
            create(objectType, data.size)
        }

        writeData(data, resumeOffset, onProgress)

        val checksum = calculateChecksum()
        val expectedCrc = Crc32.calculate(data)
        if (checksum.crc32 != expectedCrc) {
            throw DfuError.ChecksumMismatch(expected = expectedCrc, actual = checksum.crc32)
        }

        execute()
    }

    private fun resolveResumeOffset(info: DfuObjectInfo, data: ByteArray): Int {
        if (info.offset == 0) return 0
        if (info.offset > data.size) return 0

        val localCrc = Crc32.calculate(data, 0, info.offset)
        return if (localCrc == info.crc32) info.offset else 0
    }

    private fun validateResponse(response: ByteArray, expectedOpcode: Int) {
        require(response.size >= 3) { "Response too short: ${response.size} bytes" }
        require(response[0].toInt() == DfuOpcode.RESPONSE) {
            "Unexpected response opcode: 0x${response[0].toString(16)}"
        }
        require(response[1].toInt() == expectedOpcode) {
            "Response for wrong opcode: expected 0x${expectedOpcode.toString(16)}, " +
                "got 0x${response[1].toString(16)}"
        }

        val resultCode = response[2].toInt()
        if (resultCode != DfuResultCode.SUCCESS) {
            val message = if (resultCode == DfuResultCode.EXTENDED_ERROR && response.size >= 4) {
                val extCode = response[3].toInt() and 0xFF
                "DFU command 0x${expectedOpcode.toString(16)} failed: " +
                    "${DfuResultCode.describe(resultCode)} — ${DfuExtendedError.describe(extCode)}"
            } else {
                "DFU command 0x${expectedOpcode.toString(16)} failed: " +
                    DfuResultCode.describe(resultCode)
            }
            throw DfuError.ProtocolError(
                opcode = expectedOpcode,
                resultCode = resultCode,
                message = message,
            )
        }
    }
}
