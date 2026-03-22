package com.atruedev.kmpble.profiles.csc

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CscProfileTest {

    @Test
    fun cscMeasurementsEmitsParsedValues() = runTest {
        val peripheral = FakePeripheral {
            service("1816") {
                characteristic("2a5b") {
                    properties(notify = true)
                    onObserve {
                        flowOf(byteArrayOf(0x02, 0x32, 0x00, 0x00, 0x02))
                    }
                }
            }
        }
        peripheral.connect()
        val measurement = peripheral.cscMeasurements().first()
        assertEquals(50, measurement.cumulativeCrankRevolutions)
    }

    @Test
    fun readCscFeatureReturnsParsedValue() = runTest {
        val peripheral = FakePeripheral {
            service("1816") {
                characteristic("2a5c") {
                    properties(read = true)
                    onRead { byteArrayOf(0x03, 0x00) }
                }
            }
        }
        peripheral.connect()
        val feature = peripheral.readCscFeature()!!
        assertTrue(feature.wheelRevolutionDataSupported)
        assertTrue(feature.crankRevolutionDataSupported)
    }

    @Test
    fun readCscFeatureReturnsNullWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        assertNull(peripheral.readCscFeature())
    }
}
