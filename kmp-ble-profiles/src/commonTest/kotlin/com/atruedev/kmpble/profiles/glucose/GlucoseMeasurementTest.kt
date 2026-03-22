package com.atruedev.kmpble.profiles.glucose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlucoseMeasurementTest {

    @Test
    fun parseMinimalMeasurement() {
        // flags=0x00, seqNum=0x0001, baseTime=2024-03-15 10:30:45
        val data = byteArrayOf(
            0x00,                        // flags
            0x01, 0x00,                  // sequence number 1
            0xE8.toByte(), 0x07,         // year 2024
            3, 15, 10, 30, 45,           // month, day, hours, min, sec
        )
        val result = parseGlucoseMeasurement(data)!!
        assertEquals(1, result.sequenceNumber)
        assertEquals(2024, result.baseTime.year)
        assertEquals(3, result.baseTime.month)
        assertNull(result.timeOffset)
        assertNull(result.concentration)
        assertNull(result.sensorStatus)
    }

    @Test
    fun parseWithTimeOffset() {
        val data = byteArrayOf(
            0x01,                        // flags: has time offset
            0x0A, 0x00,                  // sequence number 10
            0xE8.toByte(), 0x07,         // year 2024
            6, 1, 8, 0, 0,              // month, day, hours, min, sec
            0x0F, 0x00,                  // time offset +15 minutes
        )
        val result = parseGlucoseMeasurement(data)!!
        assertEquals(15, result.timeOffset)
    }

    @Test
    fun parseWithConcentrationKgPerL() {
        // flags=0x02 (has concentration, kg/L)
        // concentration SFLOAT: mantissa=50, exp=0xF(-1) → 5.0
        val data = byteArrayOf(
            0x02,                        // flags: has concentration
            0x01, 0x00,                  // sequence number
            0xE8.toByte(), 0x07,         // year 2024
            1, 1, 0, 0, 0,              // datetime
            0x32, 0xF0.toByte(),         // SFLOAT: mantissa=50, exp=-1 → 5.0
            0x11,                        // type=CapillaryWholeBlood(1), location=Finger(1)
        )
        val result = parseGlucoseMeasurement(data)!!
        assertNotNull(result.concentration)
        assertEquals(GlucoseConcentrationUnit.KgPerL, result.unit)
        assertEquals(GlucoseType.CapillaryWholeBlood, result.type)
        assertEquals(GlucoseSampleLocation.Finger, result.sampleLocation)
    }

    @Test
    fun parseWithConcentrationMolPerL() {
        val data = byteArrayOf(
            0x06,                        // flags: has concentration + mol/L unit
            0x01, 0x00,
            0xE8.toByte(), 0x07,
            1, 1, 0, 0, 0,
            0x32, 0xF0.toByte(),         // SFLOAT
            0x21,                        // type=1, location=2(AST)
        )
        val result = parseGlucoseMeasurement(data)!!
        assertEquals(GlucoseConcentrationUnit.MolPerL, result.unit)
        assertEquals(GlucoseSampleLocation.AlternateSiteTest, result.sampleLocation)
    }

    @Test
    fun parseWithSensorStatus() {
        val data = byteArrayOf(
            0x08,                        // flags: has sensor status
            0x01, 0x00,
            0xE8.toByte(), 0x07,
            1, 1, 0, 0, 0,
            0x03, 0x00,                  // status: battery low + sensor malfunction
        )
        val result = parseGlucoseMeasurement(data)!!
        val status = result.sensorStatus!!
        assertTrue(status.batteryLow)
        assertTrue(status.sensorMalfunction)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(parseGlucoseMeasurement(byteArrayOf()))
    }

    @Test
    fun truncatedReturnsNull() {
        assertNull(parseGlucoseMeasurement(byteArrayOf(0x00, 0x01, 0x00)))
    }
}
