package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Smoke test verifying that FakePeripheral correctly exposes
 * [Peripheral.dataLengthParameters] and that the internal
 * [PeripheralContext] is accessible for test introspection.
 *
 * Note: FakePeripheral delegates [dataLengthParameters] to its
 * [FakeGattResponder], not directly to [PeripheralContext]. Both maintain
 * their own flows - the responder's flow is what Peripheral exposes.
 */
class PeripheralContextDataLengthSmokeTest {
    @Test
    fun `FakePeripheral exposes peripheralContext for test introspection`() =
        runTest {
            val peripheral = FakePeripheral { }
            val context = peripheral.peripheralContext
            assertNotNull(context, "FakePeripheral should expose peripheralContext for test access")
            peripheral.close()
        }

    @Test
    fun `FakePeripheral delegates dataLengthParameters to gattResponder`() =
        runTest {
            val peripheral = FakePeripheral { }
            val flow = peripheral.dataLengthParameters

            assertNotNull(flow, "Peripheral.dataLengthParameters must not be null")
            assertEquals(null, flow.value, "Default data length parameters should be null")
            peripheral.close()
        }

    @Test
    fun dataLengthParameters_reflects_builder_configuration() =
        runTest {
            val expected = DataLengthParameters(251, 2120, 251, 2120)
            val peripheral =
                FakePeripheral {
                    onDataLengthParameters(expected)
                }

            assertEquals(expected, peripheral.dataLengthParameters.value)
            peripheral.close()
        }
}
