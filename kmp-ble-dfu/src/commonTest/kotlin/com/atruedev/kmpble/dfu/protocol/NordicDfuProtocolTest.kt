package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.toLittleEndianBytes
import com.atruedev.kmpble.dfu.testing.FakeDfuTransport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NordicDfuProtocolTest {

    private fun selectResponse(maxSize: Int, offset: Int = 0, crc32: UInt = 0u): ByteArray {
        val resp = ByteArray(15)
        resp[0] = DfuOpcode.RESPONSE.toByte()
        resp[1] = DfuOpcode.SELECT.toByte()
        resp[2] = DfuResultCode.SUCCESS.toByte()
        maxSize.toLittleEndianBytes().copyInto(resp, 3)
        offset.toLittleEndianBytes().copyInto(resp, 7)
        crc32.toInt().toLittleEndianBytes().copyInto(resp, 11)
        return resp
    }

    private fun successResponse(opcode: Int): ByteArray =
        byteArrayOf(DfuOpcode.RESPONSE.toByte(), opcode.toByte(), DfuResultCode.SUCCESS.toByte())

    private fun checksumResponse(offset: Int, data: ByteArray): ByteArray {
        val crc = Crc32.calculate(data, 0, offset)
        val resp = ByteArray(11)
        resp[0] = DfuOpcode.RESPONSE.toByte()
        resp[1] = DfuOpcode.CALCULATE_CHECKSUM.toByte()
        resp[2] = DfuResultCode.SUCCESS.toByte()
        offset.toLittleEndianBytes().copyInto(resp, 3)
        crc.toInt().toLittleEndianBytes().copyInto(resp, 7)
        return resp
    }

    private suspend fun enqueueFull(
        transport: FakeDfuTransport,
        initPacket: ByteArray,
        firmware: ByteArray,
        maxObjectSize: Int,
    ) {
        // SET_PRN
        transport.enqueueResponse(successResponse(DfuOpcode.SET_PRN))

        // Init packet transfer
        transport.enqueueResponse(selectResponse(maxSize = maxObjectSize))
        transport.enqueueResponse(successResponse(DfuOpcode.CREATE))
        transport.enqueueResponse(checksumResponse(initPacket.size, initPacket))
        transport.enqueueResponse(successResponse(DfuOpcode.EXECUTE))

        // Firmware Select (for maxObjectSize)
        transport.enqueueResponse(selectResponse(maxSize = maxObjectSize))

        // Firmware chunks
        var offset = 0
        while (offset < firmware.size) {
            val end = minOf(offset + maxObjectSize, firmware.size)
            val chunk = firmware.copyOfRange(offset, end)

            // Select for this chunk
            transport.enqueueResponse(selectResponse(maxSize = maxObjectSize))
            // Create
            transport.enqueueResponse(successResponse(DfuOpcode.CREATE))
            // Checksum
            transport.enqueueResponse(checksumResponse(chunk.size, chunk))
            // Execute
            transport.enqueueResponse(successResponse(DfuOpcode.EXECUTE))

            offset = end
        }
    }

    @Test
    fun happyPathSmallFirmware() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val protocol = NordicDfuProtocol()

        val initPacket = ByteArray(32) { 0xAA.toByte() }
        val firmware = ByteArray(100) { it.toByte() }
        val pkg = FirmwarePackage(initPacket, firmware)

        enqueueFull(transport, initPacket, firmware, maxObjectSize = 4096)

        val options = DfuOptions(prnInterval = 0)
        val progress = protocol.performDfu(transport, pkg, options).toList()

        assertIs<DfuProgress.Starting>(progress.first())
        assertIs<DfuProgress.Completed>(progress.last())

        val transferring = progress.filterIsInstance<DfuProgress.Transferring>()
        assertTrue(transferring.isNotEmpty(), "Expected Transferring progress events")
    }

    @Test
    fun multiObjectFirmware() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val protocol = NordicDfuProtocol()

        val initPacket = ByteArray(16) { 0xBB.toByte() }
        val firmware = ByteArray(150) { it.toByte() }
        val pkg = FirmwarePackage(initPacket, firmware)
        val maxObjectSize = 64

        enqueueFull(transport, initPacket, firmware, maxObjectSize)

        val options = DfuOptions(prnInterval = 0)
        val progress = protocol.performDfu(transport, pkg, options).toList()

        assertIs<DfuProgress.Completed>(progress.last())

        val transferring = progress.filterIsInstance<DfuProgress.Transferring>()
        val lastTransfer = transferring.last()
        assertEquals(pkg.totalBytes, lastTransfer.totalBytes)
    }

    @Test
    fun progressReportsTotalObjects() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val protocol = NordicDfuProtocol()

        val initPacket = ByteArray(8) { 0xCC.toByte() }
        val firmware = ByteArray(128) { it.toByte() }
        val pkg = FirmwarePackage(initPacket, firmware)
        val maxObjectSize = 64 // 2 objects

        enqueueFull(transport, initPacket, firmware, maxObjectSize)

        val options = DfuOptions(prnInterval = 0)
        val progress = protocol.performDfu(transport, pkg, options).toList()

        val transferring = progress.filterIsInstance<DfuProgress.Transferring>()
        assertTrue(
            transferring.any { it.totalObjects == 2 },
            "Expected 2 total objects, got: ${transferring.map { it.totalObjects }.distinct()}"
        )
    }
}
