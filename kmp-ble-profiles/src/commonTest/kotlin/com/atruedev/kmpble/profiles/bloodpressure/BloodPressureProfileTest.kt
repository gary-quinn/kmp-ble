package com.atruedev.kmpble.profiles.bloodpressure

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BloodPressureProfileTest {

    @Test
    fun bloodPressureMeasurementsEmitsParsedValues() = runTest {
        val peripheral = FakePeripheral {
            service("1810") {
                characteristic("2a35") {
                    properties(indicate = true)
                    onObserve {
                        flowOf(
                            byteArrayOf(0x00, 0x78, 0x00, 0x50, 0x00, 0x5D, 0x00)
                        )
                    }
                }
            }
        }
        peripheral.connect()
        val measurement = peripheral.bloodPressureMeasurements().first()
        assertTrue(abs(measurement.systolic - 120.0f) < 0.1f)
        assertTrue(abs(measurement.diastolic - 80.0f) < 0.1f)
    }

    @Test
    fun readBloodPressureFeatureReturnsParsedValue() = runTest {
        val peripheral = FakePeripheral {
            service("1810") {
                characteristic("2a49") {
                    properties(read = true)
                    onRead { byteArrayOf(0x07, 0x00) }
                }
            }
        }
        peripheral.connect()
        val feature = peripheral.readBloodPressureFeature()!!
        assertTrue(feature.bodyMovementDetectionSupported)
        assertTrue(feature.cuffFitDetectionSupported)
        assertTrue(feature.irregularPulseDetectionSupported)
    }

    @Test
    fun readBloodPressureFeatureReturnsNullWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        assertNull(peripheral.readBloodPressureFeature())
    }
}
