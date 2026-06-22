package com.atruedev.kmpble.connection

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataLengthParametersTest {
    // --- Data class behavior ---

    @Test
    fun `DataLengthParameters stores and returns all four values`() {
        val params =
            DataLengthParameters(
                txOctets = 251,
                txTime = 2120,
                rxOctets = 251,
                rxTime = 2120,
            )
        assertEquals(251, params.txOctets)
        assertEquals(2120, params.txTime)
        assertEquals(251, params.rxOctets)
        assertEquals(2120, params.rxTime)
    }

    @Test
    fun `DataLengthParameters equality compares all fields`() {
        val a = DataLengthParameters(251, 2120, 251, 2120)
        val b = DataLengthParameters(251, 2120, 251, 2120)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `DataLengthParameters not equal when fields differ`() {
        val a = DataLengthParameters(251, 2120, 251, 2120)
        val b = DataLengthParameters(27, 2120, 251, 2120)
        assertNotEquals(a, b)
    }

    @Test
    fun `DataLengthParameters copy produces independent instance`() {
        val original = DataLengthParameters(27, 328, 27, 328)
        val copied = original.copy(txOctets = 251)
        assertEquals(251, copied.txOctets)
        assertEquals(27, original.txOctets)
        assertEquals(original.txTime, copied.txTime)
    }

    @Test
    fun `DataLengthParameters toString includes field values`() {
        val params = DataLengthParameters(27, 328, 251, 2120)
        val str = params.toString()
        assertTrue(str.contains("27"))
        assertTrue(str.contains("328"))
        assertTrue(str.contains("251"))
        assertTrue(str.contains("2120"))
    }

    @Test
    fun `DataLengthParameters individual property access is consistent`() {
        val params = DataLengthParameters(100, 500, 200, 1000)
        assertEquals(100, params.txOctets)
        assertEquals(500, params.txTime)
        assertEquals(200, params.rxOctets)
        assertEquals(1000, params.rxTime)
    }

    // --- Default platform state (null = DLE unsupported / not yet negotiated) ---

    @Test
    fun `dataLengthParameters is null on fake by default`() =
        runTest {
            val peripheral = FakePeripheral { }
            assertNull(peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters remains null after connection when not configured`() =
        runTest {
            val peripheral = FakePeripheral { }
            peripheral.connect()
            assertNull(peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    // --- Configured DLE parameters ---

    @Test
    fun `dataLengthParameters reflects configured values when set`() =
        runTest {
            val expected =
                DataLengthParameters(
                    txOctets = 251,
                    txTime = 2120,
                    rxOctets = 251,
                    rxTime = 2120,
                )
            val peripheral =
                FakePeripheral {
                    onDataLengthParameters(expected)
                }
            assertEquals(expected, peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters preserves configured values across connect and disconnect`() =
        runTest {
            val expected = DataLengthParameters(200, 1500, 200, 1500)
            val peripheral =
                FakePeripheral {
                    onDataLengthParameters(expected)
                }
            // Available before connect
            assertEquals(expected, peripheral.dataLengthParameters.value)

            peripheral.connect()
            assertEquals(expected, peripheral.dataLengthParameters.value)

            peripheral.disconnect()
            // DLE parameters survive disconnect (negotiated per connection capability,
            // but fake retains them as configured)
            assertEquals(expected, peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters can be read as StateFlow`() =
        runTest {
            val expected = DataLengthParameters(150, 1000, 150, 1000)
            val peripheral =
                FakePeripheral {
                    onDataLengthParameters(expected)
                }
            val flowValue = peripheral.dataLengthParameters.first()
            assertEquals(expected, flowValue)
            peripheral.close()
        }

    @Test
    fun `dataLengthParameters set to null simulates unsupported DLE`() =
        runTest {
            val peripheral =
                FakePeripheral {
                    onDataLengthParameters(null)
                }
            assertNull(peripheral.dataLengthParameters.value)

            peripheral.connect()
            assertNull(peripheral.dataLengthParameters.value)
            peripheral.close()
        }

    // --- Multiple builds produce independent instances ---

    @Test
    fun `different fakes can have different dataLengthParameters`() =
        runTest {
            val paramsA = DataLengthParameters(27, 328, 27, 328)
            val paramsB = DataLengthParameters(251, 2120, 251, 2120)

            val peripheralA = FakePeripheral { onDataLengthParameters(paramsA) }
            val peripheralB = FakePeripheral { onDataLengthParameters(paramsB) }

            assertEquals(paramsA, peripheralA.dataLengthParameters.value)
            assertEquals(paramsB, peripheralB.dataLengthParameters.value)
            assertNotEquals(
                peripheralA.dataLengthParameters.value,
                peripheralB.dataLengthParameters.value,
            )

            peripheralA.close()
            peripheralB.close()
        }

    // --- Mid-range values ---

    @Test
    fun `dataLengthParameters accepts mid-range values`() {
        val params =
            DataLengthParameters(
                txOctets = 128,
                txTime = 1096,
                rxOctets = 150,
                rxTime = 1280,
            )
        assertEquals(128, params.txOctets)
        assertEquals(1096, params.txTime)
        assertEquals(150, params.rxOctets)
        assertEquals(1280, params.rxTime)
    }

    // --- Close does not crash with DLE configured ---

    @Test
    fun `closing fake with configured dataLengthParameters does not throw`() =
        runTest {
            val peripheral =
                FakePeripheral {
                    onDataLengthParameters(DataLengthParameters(251, 2120, 251, 2120))
                }
            peripheral.connect()
            peripheral.close()
        }
}
