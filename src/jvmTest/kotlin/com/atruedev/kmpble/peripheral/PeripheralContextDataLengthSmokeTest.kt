package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test that verifies FakePeripheral (the common testing double used
 * to simulate both Android and iOS behavior) correctly exposes
 * Peripheral.dataLengthParameters through the same flow instance that
 * PeripheralContext owns.
 *
 * Both AndroidPeripheral and IosPeripheral follow the same pattern:
 * delegating to PeripheralContext.dataLengthParameters.
 */
class PeripheralContextDataLengthSmokeTest {
    @Test
    fun `PeripheralContext owns dataLengthParameters flow`() =
        runTest {
            val peripheral = FakePeripheral { }
            val context = peripheral.peripheralContext
            val flow = peripheral.dataLengthParameters

            assertNotNull(flow, "Peripheral.dataLengthParameters must not be null")
            assertEquals(null, flow.value, "Default data length parameters should be null")
            assertTrue(
                context.dataLengthParameters === flow,
                "PeripheralContext.dataLengthParameters must be the same flow instance",
            )
            peripheral.close()
        }

    @Test
    fun dataLengthParameters_flow_type_is_StateFlow() =
        runTest {
            val peripheral = FakePeripheral { }
            assertTrue(
                peripheral.dataLengthParameters is StateFlow<DataLengthParameters?>,
                "dataLengthParameters must be a StateFlow<DataLengthParameters?>",
            )
            peripheral.close()
        }

    @Test
    fun dataLengthParameters_reflects_context_update() =
        runTest {
            val expected = DataLengthParameters(251, 2120, 251, 2120)
            val peripheral = FakePeripheral { onDataLengthParameters(expected) }

            assertEquals(expected, peripheral.dataLengthParameters.value)
            assertEquals(expected, peripheral.peripheralContext.dataLengthParameters.value)
            peripheral.close()
        }
}
