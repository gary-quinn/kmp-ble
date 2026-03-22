package com.atruedev.kmpble.profiles.csc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CscMeasurementTest {

    @Test
    fun parseWheelOnly() {
        // flags=0x01, wheelRevs=0x00000064 (100), lastWheelTime=0x0400 (1024)
        val data = byteArrayOf(0x01, 0x64, 0x00, 0x00, 0x00, 0x00, 0x04)
        val result = parseCscMeasurement(data)!!
        assertEquals(100L, result.cumulativeWheelRevolutions)
        assertEquals(1024, result.lastWheelEventTime)
        assertNull(result.cumulativeCrankRevolutions)
        assertNull(result.lastCrankEventTime)
    }

    @Test
    fun parseCrankOnly() {
        // flags=0x02, crankRevs=0x0032 (50), lastCrankTime=0x0200 (512)
        val data = byteArrayOf(0x02, 0x32, 0x00, 0x00, 0x02)
        val result = parseCscMeasurement(data)!!
        assertNull(result.cumulativeWheelRevolutions)
        assertEquals(50, result.cumulativeCrankRevolutions)
        assertEquals(512, result.lastCrankEventTime)
    }

    @Test
    fun parseBothWheelAndCrank() {
        // flags=0x03
        val data = byteArrayOf(
            0x03,
            0x0A, 0x00, 0x00, 0x00,   // wheelRevs=10
            0x00, 0x01,                 // lastWheelTime=256
            0x05, 0x00,                 // crankRevs=5
            0x80.toByte(), 0x00,        // lastCrankTime=128
        )
        val result = parseCscMeasurement(data)!!
        assertEquals(10L, result.cumulativeWheelRevolutions)
        assertEquals(256, result.lastWheelEventTime)
        assertEquals(5, result.cumulativeCrankRevolutions)
        assertEquals(128, result.lastCrankEventTime)
    }

    @Test
    fun noDataPresent() {
        val data = byteArrayOf(0x00)
        val result = parseCscMeasurement(data)!!
        assertNull(result.cumulativeWheelRevolutions)
        assertNull(result.cumulativeCrankRevolutions)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(parseCscMeasurement(byteArrayOf()))
    }

    @Test
    fun truncatedWheelDataReturnsNull() {
        assertNull(parseCscMeasurement(byteArrayOf(0x01, 0x64, 0x00)))
    }
}
