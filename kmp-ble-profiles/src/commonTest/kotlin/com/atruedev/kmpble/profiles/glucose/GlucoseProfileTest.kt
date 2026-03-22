package com.atruedev.kmpble.profiles.glucose

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlucoseProfileTest {

    private val minimalGlucoseData = byteArrayOf(
        0x00,
        0x01, 0x00,
        0xE8.toByte(), 0x07,
        3, 15, 10, 30, 45,
    )

    @Test
    fun glucoseMeasurementsEmitsParsedValues() = runTest {
        val peripheral = FakePeripheral {
            service("1808") {
                characteristic("2a18") {
                    properties(notify = true)
                    onObserve { flowOf(minimalGlucoseData) }
                }
            }
        }
        peripheral.connect()
        val measurement = peripheral.glucoseMeasurements().first()
        assertEquals(1, measurement.sequenceNumber)
        assertEquals(2024, measurement.baseTime.year)
    }

    @Test
    fun readGlucoseFeatureReturnsParsedValue() = runTest {
        val peripheral = FakePeripheral {
            service("1808") {
                characteristic("2a51") {
                    properties(read = true)
                    onRead { byteArrayOf(0x03, 0x00) }
                }
            }
        }
        peripheral.connect()
        val feature = peripheral.readGlucoseFeature()!!
        assertTrue(feature.lowBatteryDetectionSupported)
        assertTrue(feature.sensorMalfunctionDetectionSupported)
    }

    @Test
    fun readGlucoseFeatureReturnsNullWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        assertNull(peripheral.readGlucoseFeature())
    }

    @Test
    fun glucoseMeasurementContextsEmitsParsedValues() = runTest {
        // flags=0x02 (has meal), seqNum=5, meal=Fasting(3)
        val contextData = byteArrayOf(0x02, 0x05, 0x00, 0x03)
        val peripheral = FakePeripheral {
            service("1808") {
                characteristic("2a34") {
                    properties(notify = true)
                    onObserve { flowOf(contextData) }
                }
            }
        }
        peripheral.connect()
        val context = peripheral.glucoseMeasurementContexts().first()
        assertEquals(5, context.sequenceNumber)
        assertEquals(Meal.Fasting, context.meal)
    }
}
