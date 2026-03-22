package com.atruedev.kmpble.profiles.heartrate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeartRateMeasurementTest {

    @Test
    fun parse8BitHeartRate() {
        val data = byteArrayOf(0x00, 72)
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(72, result.heartRate)
        assertNull(result.sensorContactDetected)
        assertNull(result.energyExpended)
        assertTrue(result.rrIntervals.isEmpty())
    }

    @Test
    fun parse16BitHeartRate() {
        // flags=0x01 (16-bit HR), HR=0x0100 (256) little-endian
        val data = byteArrayOf(0x01, 0x00, 0x01)
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(256, result.heartRate)
    }

    @Test
    fun parseSensorContactDetected() {
        // flags=0x06 (contact supported + detected), HR=80
        val data = byteArrayOf(0x06, 80)
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(true, result.sensorContactDetected)
    }

    @Test
    fun parseSensorContactNotDetected() {
        // flags=0x04 (contact supported, not detected), HR=80
        val data = byteArrayOf(0x04, 80)
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(false, result.sensorContactDetected)
    }

    @Test
    fun parseEnergyExpended() {
        // flags=0x08 (energy present), HR=65, energy=0x00C8 (200) little-endian
        val data = byteArrayOf(0x08, 65, 0xC8.toByte(), 0x00)
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(65, result.heartRate)
        assertEquals(200, result.energyExpended)
    }

    @Test
    fun parseRrIntervals() {
        // flags=0x10 (RR present), HR=70, two RR values
        // RR1=0x0400 (1024 → 1000ms), RR2=0x0380 (896 → 875ms)
        val data = byteArrayOf(0x10, 70, 0x00, 0x04, 0x80.toByte(), 0x03)
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(70, result.heartRate)
        assertEquals(2, result.rrIntervals.size)
        assertEquals(1000, result.rrIntervals[0])
        assertEquals(875, result.rrIntervals[1])
    }

    @Test
    fun parseAllFields() {
        // flags=0x1F (16-bit HR + contact supported/detected + energy + RR)
        // HR=0x012C (300), energy=0x0064 (100), RR=0x0400 (1024)
        val data = byteArrayOf(
            0x1F,
            0x2C, 0x01,       // HR 300
            0x64, 0x00,       // energy 100
            0x00, 0x04,       // RR 1024
        )
        val result = parseHeartRateMeasurement(data)!!
        assertEquals(300, result.heartRate)
        assertEquals(true, result.sensorContactDetected)
        assertEquals(100, result.energyExpended)
        assertEquals(1, result.rrIntervals.size)
    }

    @Test
    fun emptyDataReturnsNull() {
        assertNull(parseHeartRateMeasurement(byteArrayOf()))
    }

    @Test
    fun singleByteReturnsNull() {
        assertNull(parseHeartRateMeasurement(byteArrayOf(0x00)))
    }

    @Test
    fun truncated16BitReturnsNull() {
        // flags=0x01 (16-bit HR) but only 1 byte of HR data
        assertNull(parseHeartRateMeasurement(byteArrayOf(0x01, 0x00)))
    }
}
