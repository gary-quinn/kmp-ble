package com.atruedev.kmpble.dfu.protocol.smp

import kotlin.test.Test
import kotlin.test.assertEquals

class SmpHeaderTest {

    @Test
    fun roundTripEncodeDecode() {
        val header = SmpHeader(
            op = SmpOp.WRITE,
            flags = 0,
            length = 256,
            group = SmpGroup.IMAGE_MGMT,
            sequence = 42,
            commandId = SmpCommand.IMAGE_UPLOAD,
        )
        val encoded = header.encode()
        assertEquals(SmpHeader.SIZE, encoded.size)

        val decoded = SmpHeader.decode(encoded)
        assertEquals(header, decoded)
    }

    @Test
    fun encodesBigEndianLength() {
        val header = SmpHeader(op = 0, flags = 0, length = 0x0102, group = 0, sequence = 0, commandId = 0)
        val encoded = header.encode()
        assertEquals(0x01.toByte(), encoded[2])
        assertEquals(0x02.toByte(), encoded[3])
    }

    @Test
    fun encodesBigEndianGroup() {
        val header = SmpHeader(op = 0, flags = 0, length = 0, group = 0x0304, sequence = 0, commandId = 0)
        val encoded = header.encode()
        assertEquals(0x03.toByte(), encoded[4])
        assertEquals(0x04.toByte(), encoded[5])
    }

    @Test
    fun decodeFromOffset() {
        val prefix = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val header = SmpHeader(op = 2, flags = 0, length = 10, group = 1, sequence = 5, commandId = 1)
        val data = prefix + header.encode()
        val decoded = SmpHeader.decode(data, offset = 2)
        assertEquals(header, decoded)
    }

    @Test
    fun zeroLengthHeader() {
        val header = SmpHeader(op = SmpOp.READ, flags = 0, length = 0, group = 0, sequence = 0, commandId = 0)
        val encoded = header.encode()
        val decoded = SmpHeader.decode(encoded)
        assertEquals(0, decoded.length)
        assertEquals(SmpOp.READ, decoded.op)
    }
}
