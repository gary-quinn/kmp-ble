package com.atruedev.kmpble.profiles.heartrate

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeartRateProfileTest {

    @Test
    fun heartRateMeasurementsEmitsParsedValues() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true)
                    onObserve { flowOf(byteArrayOf(0x00, 72), byteArrayOf(0x00, 85)) }
                }
            }
        }
        peripheral.connect()
        val measurements = peripheral.heartRateMeasurements().toList()
        assertEquals(72, measurements[0].heartRate)
        assertEquals(85, measurements[1].heartRate)
    }

    @Test
    fun heartRateMeasurementsReturnsEmptyFlowWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val measurements = peripheral.heartRateMeasurements().toList()
        assertTrue(measurements.isEmpty())
    }

    @Test
    fun readBodySensorLocationReturnsParsedValue() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a38") {
                    properties(read = true)
                    onRead { byteArrayOf(1) }
                }
            }
        }
        peripheral.connect()
        assertEquals(BodySensorLocation.Chest, peripheral.readBodySensorLocation())
    }

    @Test
    fun readBodySensorLocationReturnsNullWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        assertNull(peripheral.readBodySensorLocation())
    }
}
