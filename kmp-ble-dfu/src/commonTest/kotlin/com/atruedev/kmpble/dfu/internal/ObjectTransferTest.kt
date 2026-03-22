package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.protocol.Crc32
import com.atruedev.kmpble.dfu.protocol.DfuExtendedError
import com.atruedev.kmpble.dfu.protocol.DfuObjectType
import com.atruedev.kmpble.dfu.protocol.DfuOpcode
import com.atruedev.kmpble.dfu.protocol.DfuResultCode
import com.atruedev.kmpble.dfu.testing.FakeDfuTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObjectTransferTest {

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

    private fun checksumResponse(offset: Int, crc32: UInt): ByteArray {
        val resp = ByteArray(11)
        resp[0] = DfuOpcode.RESPONSE.toByte()
        resp[1] = DfuOpcode.CALCULATE_CHECKSUM.toByte()
        resp[2] = DfuResultCode.SUCCESS.toByte()
        offset.toLittleEndianBytes().copyInto(resp, 3)
        crc32.toInt().toLittleEndianBytes().copyInto(resp, 7)
        return resp
    }

    @Test
    fun selectParsesObjectInfo() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val transfer = ObjectTransfer(transport, prnInterval = 0)

        launch {
            transport.enqueueResponse(selectResponse(maxSize = 4096, offset = 100, crc32 = 0xDEADBEEFu))
        }

        val info = transfer.select(DfuObjectType.COMMAND)
        assertEquals(4096, info.maxSize)
        assertEquals(100, info.offset)
        assertEquals(0xDEADBEEFu, info.crc32)
    }

    @Test
    fun transferObjectFullCycle() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val transfer = ObjectTransfer(transport, prnInterval = 0)
        val data = ByteArray(50) { it.toByte() }
        val expectedCrc = Crc32.calculate(data)

        launch {
            // Select: no prior progress
            transport.enqueueResponse(selectResponse(maxSize = 4096, offset = 0, crc32 = 0u))
            // Create
            transport.enqueueResponse(successResponse(DfuOpcode.CREATE))
            // Checksum
            transport.enqueueResponse(checksumResponse(data.size, expectedCrc))
            // Execute
            transport.enqueueResponse(successResponse(DfuOpcode.EXECUTE))
        }

        transfer.transferObject(DfuObjectType.COMMAND, data)

        val commands = transport.getCommandLog()
        assertEquals(DfuOpcode.SELECT.toByte(), commands[0][0])
        assertEquals(DfuOpcode.CREATE.toByte(), commands[1][0])
        assertEquals(DfuOpcode.CALCULATE_CHECKSUM.toByte(), commands[2][0])
        assertEquals(DfuOpcode.EXECUTE.toByte(), commands[3][0])
    }

    @Test
    fun transferObjectResumesFromOffset() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val transfer = ObjectTransfer(transport, prnInterval = 0)
        val data = ByteArray(50) { it.toByte() }

        // Device has all data already
        val fullCrc = Crc32.calculate(data)

        launch {
            // Select: device has full data
            transport.enqueueResponse(selectResponse(maxSize = 4096, offset = data.size, crc32 = fullCrc))
            // Execute (skip create + write)
            transport.enqueueResponse(successResponse(DfuOpcode.EXECUTE))
        }

        transfer.transferObject(DfuObjectType.COMMAND, data)

        val commands = transport.getCommandLog()
        assertEquals(2, commands.size) // Select + Execute only
        assertEquals(0, transport.getDataLog().size) // No data written
    }

    @Test
    fun checksumMismatchThrows() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val transfer = ObjectTransfer(transport, prnInterval = 0)
        val data = ByteArray(10) { it.toByte() }

        launch {
            transport.enqueueResponse(selectResponse(maxSize = 4096))
            transport.enqueueResponse(successResponse(DfuOpcode.CREATE))
            transport.enqueueResponse(checksumResponse(data.size, 0xBADBADBAu))
        }

        assertFailsWith<DfuError.ChecksumMismatch> {
            transfer.transferObject(DfuObjectType.DATA, data)
        }
    }

    @Test
    fun protocolErrorThrows() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val transfer = ObjectTransfer(transport, prnInterval = 0)

        launch {
            transport.enqueueResponse(
                byteArrayOf(DfuOpcode.RESPONSE.toByte(), DfuOpcode.SELECT.toByte(), DfuResultCode.OPERATION_FAILED.toByte())
                    + ByteArray(12)
            )
        }

        assertFailsWith<DfuError.ProtocolError> {
            transfer.select(DfuObjectType.COMMAND)
        }
    }

    @Test
    fun extendedErrorIncludesDetail() = runTest {
        val transport = FakeDfuTransport(mtu = 20)
        val transfer = ObjectTransfer(transport, prnInterval = 0)

        launch {
            transport.enqueueResponse(
                byteArrayOf(
                    DfuOpcode.RESPONSE.toByte(),
                    DfuOpcode.SELECT.toByte(),
                    DfuResultCode.EXTENDED_ERROR.toByte(),
                    DfuExtendedError.SIGNATURE_MISSING.toByte(),
                ) + ByteArray(11)
            )
        }

        val error = assertFailsWith<DfuError.ProtocolError> {
            transfer.select(DfuObjectType.COMMAND)
        }
        assertContains(error.message!!, "Signature missing")
    }
}
