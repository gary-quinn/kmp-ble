package com.atruedev.kmpble.dfu.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {

    @Test
    fun emptyInput() {
        assertEquals(0x00000000u, Crc32.calculate(byteArrayOf()))
    }

    @Test
    fun singleByte() {
        assertEquals(0xE8B7BE43u, Crc32.calculate(byteArrayOf(0x61))) // 'a'
    }

    @Test
    fun standardTestVector() {
        // "123456789" → 0xCBF43926
        val data = "123456789".encodeToByteArray()
        assertEquals(0xCBF43926u, Crc32.calculate(data))
    }

    @Test
    fun incrementalMatchesSinglePass() {
        val data = "Hello, World!".encodeToByteArray()
        val singlePass = Crc32.calculate(data)

        val mid = data.size / 2
        val running1 = Crc32.update(0xFFFFFFFFu, data, 0, mid)
        val running2 = Crc32.update(running1, data, mid, data.size - mid)
        val incremental = Crc32.finalize(running2)

        assertEquals(singlePass, incremental)
    }

    @Test
    fun offsetAndLength() {
        val full = "abcdefgh".encodeToByteArray()
        val slice = "cdef".encodeToByteArray()
        assertEquals(Crc32.calculate(slice), Crc32.calculate(full, offset = 2, length = 4))
    }

    @Test
    fun resumeFromPreviousCrc() {
        val data = "123456789".encodeToByteArray()
        val mid = 5

        val firstHalf = Crc32.calculate(data, 0, mid)
        val resumed = Crc32.resume(firstHalf)
        val continued = Crc32.update(resumed, data, mid, data.size - mid)
        val result = Crc32.finalize(continued)

        assertEquals(Crc32.calculate(data), result)
    }
}
