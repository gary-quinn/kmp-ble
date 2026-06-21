package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionParamPreset
import com.atruedev.kmpble.connection.requestConnectionParameterPreset
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class ConnectionParamPresetTest {
    @Test
    fun balancedMapsToNordicDefaults() {
        val params = ConnectionParamPreset.BALANCED.toConnectionParameters()
        assertEquals(30.milliseconds, params.intervalRange.start)
        assertEquals(50.milliseconds, params.intervalRange.endInclusive)
        assertEquals(0, params.slaveLatency)
        assertEquals(4000.milliseconds, params.supervisionTimeout)
    }

    @Test
    fun highThroughputMapsToNordicDefaults() {
        val params = ConnectionParamPreset.HIGH_THROUGHPUT.toConnectionParameters()
        assertEquals(7.5.milliseconds, params.intervalRange.start)
        assertEquals(15.milliseconds, params.intervalRange.endInclusive)
        assertEquals(0, params.slaveLatency)
        assertEquals(4000.milliseconds, params.supervisionTimeout)
    }

    @Test
    fun powerSavingMapsToNordicDefaults() {
        val params = ConnectionParamPreset.POWER_SAVING.toConnectionParameters()
        assertEquals(1000.milliseconds, params.intervalRange.start)
        assertEquals(2000.milliseconds, params.intervalRange.endInclusive)
        assertEquals(2, params.slaveLatency)
        assertEquals(6000.milliseconds, params.supervisionTimeout)
    }

    @Test
    fun hidMapsToNordicDefaults() {
        val params = ConnectionParamPreset.HID.toConnectionParameters()
        assertEquals(11.25.milliseconds, params.intervalRange.start)
        assertEquals(11.25.milliseconds, params.intervalRange.endInclusive)
        assertEquals(0, params.slaveLatency)
        assertEquals(500.milliseconds, params.supervisionTimeout)
    }

    @Test
    fun allPresetsProduceValidConnectionParameters() {
        for (preset in ConnectionParamPreset.entries) {
            // Should not throw - all presets must produce valid parameters
            preset.toConnectionParameters()
        }
    }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun requestPresetDelegatesToRequestConnectionParameterUpdate() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect()

            val result =
                peripheral.requestConnectionParameterPreset(
                    ConnectionParamPreset.BALANCED,
                )
            assertNotNull(result)
            assertEquals(50.milliseconds, result.negotiatedInterval)
            assertEquals(0, result.negotiatedLatency)
            assertEquals(4000.milliseconds, result.negotiatedSupervisionTimeout)
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun requestPowerSavingPresetReturnsCorrectNegotiatedValues() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect()

            val result =
                peripheral.requestConnectionParameterPreset(
                    ConnectionParamPreset.POWER_SAVING,
                )
            assertNotNull(result)
            assertEquals(2000.milliseconds, result.negotiatedInterval)
            assertEquals(2, result.negotiatedLatency)
            assertEquals(6000.milliseconds, result.negotiatedSupervisionTimeout)
        }
}
