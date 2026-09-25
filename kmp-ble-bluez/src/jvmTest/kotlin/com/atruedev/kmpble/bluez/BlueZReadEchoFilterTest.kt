package com.atruedev.kmpble.bluez

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlueZReadEchoFilterTest {
    private var now = 0L
    private val filter = BlueZReadEchoFilter(echoWindowNanos = 100, clock = { now })

    @Test
    fun echoArrivingDuringTheReadIsDropped() {
        filter.beginRead("/c")
        assertFalse(filter.admit("/c", byteArrayOf(1)))
        assertFalse(filter.admit("/c", byteArrayOf(2)))
        val released = filter.endRead("/c", byteArrayOf(1))
        assertEquals(1, released.size)
        assertContentEquals(byteArrayOf(2), released.single())
        assertTrue(filter.admit("/c", byteArrayOf(1)))
    }

    @Test
    fun echoArrivingAfterTheReplyIsDroppedOnceWithinTheWindow() {
        filter.beginRead("/c")
        assertTrue(filter.endRead("/c", byteArrayOf(7)).isEmpty())
        assertFalse(filter.admit("/c", byteArrayOf(7)))
        assertTrue(filter.admit("/c", byteArrayOf(7)))

        filter.beginRead("/c")
        filter.endRead("/c", byteArrayOf(8))
        now += 1_000
        assertTrue(filter.admit("/c", byteArrayOf(8)))
    }

    @Test
    fun otherCharacteristicsAndFailedReadsPassThrough() {
        filter.beginRead("/a")
        assertTrue(filter.admit("/b", byteArrayOf(1)))
        assertFalse(filter.admit("/a", byteArrayOf(3)))
        assertContentEquals(byteArrayOf(3), filter.endRead("/a", null).single())
    }
}
