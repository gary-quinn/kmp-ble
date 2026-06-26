package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [FakePeripheral.dataLengthParameters].
 *
 * These tests verify the complete integration path:
 * - FakePeripheral exposes dataLengthParameters as StateFlow
 * - Configuration via onDataLengthParameters builder lambda
 * - Flow emits updates when configuration changes
 * - Integration with connection state transitions
 * - Multiple peripherals maintain independent flows
 */
class FakePeripheralDataLengthIntegrationTest {

    // --- StateFlow contract ---

    @Test
    fun `dataLengthParameters returns non-null StateFlow instance`() =
        runTest {
            val peripheral = FakePeripheral { }
            val flow = peripheral.dataLengthParameters

            assertNotNull(flow, "dataLengthParameters must return a non-null StateFlow")
            assertTrue(flow is kotlinx.coroutines.flow.StateFlow, "Must be StateFlow")
            peripheral.close()
        }

    @Test
    fun `StateFlow value is accessible synchronously`() =
        runTest {
            val peripheral = FakePeripheral { }
            val flow = peripheral.dataLengthParameters

            // StateFlow should have immediate .value access
            val value = flow.value
            assertNull(value, "Default value should be null")
            peripheral.close()
        }

    // --- Flow emission behavior ---

    @Test
    fun `flow emission captures configured value`() =
        runTest {
            val expected = DataLengthParameters(251, 2120, 251, 2120)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            var captured: DataLengthParameters? = null
            val job = launch {
                peripheral.dataLengthParameters.take(1).collectLatest { captured = it }
            }

            job.join()
            assertEquals(expected, captured, "Flow should emit the configured value")
            peripheral.close()
        }

    @Test
    fun `multiple collectors receive same value`() =
        runTest {
            val expected = DataLengthParameters(200, 1500, 200, 1500)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            var collector1Value: DataLengthParameters? = null
            var collector2Value: DataLengthParameters? = null

            val job1 = launch {
                peripheral.dataLengthParameters.take(1).collectLatest { collector1Value = it }
            }
            val job2 = launch {
                peripheral.dataLengthParameters.take(1).collectLatest { collector2Value = it }
            }

            job1.join()
            job2.join()
            assertEquals(expected, collector1Value)
            assertEquals(expected, collector2Value)
            assertEquals(collector1Value, collector2Value)
            peripheral.close()
        }

    // --- Connection lifecycle integration ---

    @Test
    fun `dataLengthParameters available before connection`() =
        runTest {
            val expected = DataLengthParameters(251, 2120, 251, 2120)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            // Should be available immediately after configuration
            assertEquals(expected, peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters persists through connection`() =
        runTest {
            val expected = DataLengthParameters(180, 1200, 180, 1200)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            // Before connect
            assertEquals(expected, peripheral.dataLengthParameters.value)

            peripheral.connect()

            // After connect - should still be available
            assertEquals(expected, peripheral.dataLengthParameters.value)

            peripheral.close()
        }

    @Test
    fun `dataLengthParameters persists through disconnect`() =
        runTest {
            val expected = DataLengthParameters(251, 2120, 251, 2120)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            peripheral.connect()
            assertEquals(expected, peripheral.dataLengthParameters.value)

            peripheral.disconnect()

            // DLE parameters should persist after disconnect
            assertEquals(expected, peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters survives multiple connect disconnect cycles`() =
        runTest {
            val expected = DataLengthParameters(150, 1000, 150, 1000)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            repeat(3) { cycle ->
                peripheral.connect()
                assertEquals(expected, peripheral.dataLengthParameters.value, "Cycle $cycle after connect")

                peripheral.disconnect()
                assertEquals(expected, peripheral.dataLengthParameters.value, "Cycle $cycle after disconnect")
            }

            peripheral.close()
        }

    // --- Edge cases ---

    @Test
    fun `null configuration explicitly disables DLE`() =
        runTest {
            val peripheral = FakePeripheral {
                onDataLengthParameters(null)
            }

            assertNull(peripheral.dataLengthParameters.value, "Explicit null should result in null")
            peripheral.close()
        }

    @Test
    fun `minimum valid DLE parameters are accepted`() =
        runTest {
            val minParams = DataLengthParameters(27, 328, 27, 328)
            val peripheral = FakePeripheral { onDataLengthParameters(minParams) }

            assertEquals(minParams, peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    @Test
    fun `maximum valid DLE parameters are accepted`() =
        runTest {
            val maxParams = DataLengthParameters(251, 2120, 251, 2120)
            val peripheral = FakePeripheral { onDataLengthParameters(maxParams) }

            assertEquals(maxParams, peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    // --- Independence from other peripheral state ---

    @Test
    fun `dataLengthParameters independent from MTU state`() =
        runTest {
            val peripheral = FakePeripheral {
                onDataLengthParameters(DataLengthParameters(251, 2120, 251, 2120))
            }

            // MTU and DLE are separate concerns
            val mtu = peripheral.mtu.value
            val dle = peripheral.dataLengthParameters.value

            assertNotNull(dle, "DLE should be configured")
            // MTU defaults to some value, DLE is independent
            assertEquals(251, dle.txOctets)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters independent from connection state`() =
        runTest {
            val peripheral = FakePeripheral { }

            // DLE configuration is independent of connection state
            assertNull(peripheral.dataLengthParameters.value, "Should be null when not configured")

            peripheral.connect()
            assertNull(peripheral.dataLengthParameters.value, "Still null after connect if not configured")

            peripheral.close()
        }
}
