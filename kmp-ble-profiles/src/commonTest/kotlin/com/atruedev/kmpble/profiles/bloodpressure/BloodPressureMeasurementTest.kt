package com.atruedev.kmpble.profiles.bloodpressure

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BloodPressureMeasurementTest {

    @Test
    fun parseMinimalMmHg() {
        // flags=0x00 (mmHg, no optional fields)
        // systolic=120.0 → SFLOAT mantissa=120, exp=0 → 0x0078
        // diastolic=80.0 → SFLOAT mantissa=80, exp=0 → 0x0050
        // MAP=93.0 → SFLOAT mantissa=93, exp=0 → 0x005D
        val data = byteArrayOf(
            0x00,
            0x78, 0x00,   // systolic 120
            0x50, 0x00,   // diastolic 80
            0x5D, 0x00,   // MAP 93
        )
        val result = parseBloodPressureMeasurement(data)!!
        assertTrue(abs(result.systolic - 120.0f) < 0.1f)
        assertTrue(abs(result.diastolic - 80.0f) < 0.1f)
        assertTrue(abs(result.meanArterialPressure - 93.0f) < 0.1f)
        assertEquals(BloodPressureUnit.MmHg, result.unit)
        assertNull(result.timestamp)
        assertNull(result.pulseRate)
        assertNull(result.userId)
        assertNull(result.measurementStatus)
    }

    @Test
    fun parseKpaUnit() {
        val data = byteArrayOf(
            0x01,           // flags: kPa unit
            0x64, 0x00,     // systolic 100
            0x50, 0x00,     // diastolic 80
            0x5A, 0x00,     // MAP 90
        )
        val result = parseBloodPressureMeasurement(data)!!
        assertEquals(BloodPressureUnit.KPa, result.unit)
    }

    @Test
    fun parseWithTimestamp() {
        val data = byteArrayOf(
            0x02,           // flags: has timestamp
            0x78, 0x00,     // systolic
            0x50, 0x00,     // diastolic
            0x5D, 0x00,     // MAP
            0xE8.toByte(), 0x07,  // year 2024
            3, 15, 10, 30, 45,   // month, day, hours, min, sec
        )
        val result = parseBloodPressureMeasurement(data)!!
        val ts = result.timestamp!!
        assertEquals(2024, ts.year)
        assertEquals(3, ts.month)
        assertEquals(15, ts.day)
    }

    @Test
    fun parseWithMeasurementStatus() {
        val data = byteArrayOf(
            0x10,           // flags: has measurement status
            0x78, 0x00,     // systolic
            0x50, 0x00,     // diastolic
            0x5D, 0x00,     // MAP
            0x05, 0x00,     // status: body movement + irregular pulse
        )
        val result = parseBloodPressureMeasurement(data)!!
        val status = result.measurementStatus!!
        assertTrue(status.bodyMovementDetected)
        assertTrue(status.irregularPulseDetected)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(parseBloodPressureMeasurement(byteArrayOf()))
    }

    @Test
    fun truncatedReturnsNull() {
        assertNull(parseBloodPressureMeasurement(byteArrayOf(0x00, 0x78, 0x00)))
    }
}
