package com.atruedev.kmpble.gatt

import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.MtuExceededException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LargeWriteTest {

    @Test
    fun dataWithinMtuNotChunked() {
        val data = ByteArray(20) { it.toByte() }
        assertFalse(LargeWriteHandler.shouldChunk(data, maxLength = 20))
        assertEquals(1, LargeWriteHandler.chunk(data, maxLength = 20).size)
    }

    @Test
    fun dataExceedingMtuIsChunked() {
        val data = ByteArray(50) { it.toByte() }
        assertTrue(LargeWriteHandler.shouldChunk(data, maxLength = 20))

        val chunks = LargeWriteHandler.chunk(data, maxLength = 20)
        assertEquals(3, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
        assertEquals(10, chunks[2].size)

        // Verify content integrity
        val reassembled = chunks.fold(byteArrayOf()) { acc, chunk -> acc + chunk }
        assertContentEquals(data, reassembled)
    }

    @Test
    fun singleByteChunkEdgeCase() {
        val data = ByteArray(1) { 0x42 }
        assertFalse(LargeWriteHandler.shouldChunk(data, maxLength = 20))
        val chunks = LargeWriteHandler.chunk(data, maxLength = 20)
        assertEquals(1, chunks.size)
        assertContentEquals(byteArrayOf(0x42), chunks[0])
    }

    @Test
    fun exactMtuBoundary() {
        val data = ByteArray(40) { it.toByte() }
        val chunks = LargeWriteHandler.chunk(data, maxLength = 20)
        assertEquals(2, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
    }

    @Test
    fun signedWriteThrowsOnOversized() {
        val data = ByteArray(25) { it.toByte() }
        assertFailsWith<MtuExceededException> {
            LargeWriteHandler.validateForWriteType(data, maxLength = 20, WriteType.Signed)
        }
    }

    @Test
    fun signedWriteAllowsWithinMtu() {
        val data = ByteArray(20) { it.toByte() }
        LargeWriteHandler.validateForWriteType(data, maxLength = 20, WriteType.Signed)
    }

    @Test
    fun nonSignedWriteDoesNotThrow() {
        val data = ByteArray(50) { it.toByte() }
        LargeWriteHandler.validateForWriteType(data, maxLength = 20, WriteType.WithResponse)
        LargeWriteHandler.validateForWriteType(data, maxLength = 20, WriteType.WithoutResponse)
    }
}
