package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class ConnectionParametersTest {
    // --- Parameter validation (reject) ---

    @Test
    fun `ConnectionParameters rejects non-positive interval start`() {
        try {
            ConnectionParameters(
                intervalRange = 0.milliseconds..10.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 1000.milliseconds,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("must be positive" in e.message.orEmpty().lowercase())
        }
    }

    @Test
    fun `ConnectionParameters rejects endInclusive before start`() {
        try {
            ConnectionParameters(
                intervalRange = 30.milliseconds..15.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 1000.milliseconds,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(">=" in e.message.orEmpty())
        }
    }

    @Test
    fun `ConnectionParameters rejects negative slaveLatency`() {
        try {
            ConnectionParameters(
                intervalRange = 15.milliseconds..30.milliseconds,
                slaveLatency = -1,
                supervisionTimeout = 1000.milliseconds,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("0..499" in e.message.orEmpty())
        }
    }

    @Test
    fun `ConnectionParameters rejects slaveLatency above 499`() {
        try {
            ConnectionParameters(
                intervalRange = 15.milliseconds..30.milliseconds,
                slaveLatency = 500,
                supervisionTimeout = 1000.milliseconds,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("0..499" in e.message.orEmpty())
        }
    }

    @Test
    fun `ConnectionParameters rejects non-positive supervision timeout`() {
        try {
            ConnectionParameters(
                intervalRange = 15.milliseconds..30.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 0.milliseconds,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("supervision" in e.message.orEmpty().lowercase())
        }
    }

    // --- Parameter validation (accept boundaries) ---

    @Test
    fun `ConnectionParameters accepts minimum valid values`() {
        ConnectionParameters(
            intervalRange = 1.milliseconds..1.milliseconds,
            slaveLatency = 0,
            supervisionTimeout = 1.milliseconds,
        )
    }

    @Test
    fun `ConnectionParameters accepts maximum slaveLatency`() {
        ConnectionParameters(
            intervalRange = 100.milliseconds..200.milliseconds,
            slaveLatency = 499,
            supervisionTimeout = 5000.milliseconds,
        )
    }

    @Test
    fun `ConnectionParameters accepts equal interval start and end`() {
        ConnectionParameters(
            intervalRange = 50.milliseconds..50.milliseconds,
            slaveLatency = 0,
            supervisionTimeout = 1000.milliseconds,
        )
    }

    @Test
    fun `ConnectionParameters accepts typical balanced values`() {
        val params =
            ConnectionParameters(
                intervalRange = 30.milliseconds..50.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 4000.milliseconds,
            )
        assertEquals(30.milliseconds, params.intervalRange.start)
        assertEquals(50.milliseconds, params.intervalRange.endInclusive)
        assertEquals(0, params.slaveLatency)
        assertEquals(4000.milliseconds, params.supervisionTimeout)
    }

    // --- Data class behavior ---

    @Test
    fun `ConnectionParameters equality compares all fields`() {
        val a =
            ConnectionParameters(
                intervalRange = 15.milliseconds..30.milliseconds,
                slaveLatency = 4,
                supervisionTimeout = 2000.milliseconds,
            )
        val b =
            ConnectionParameters(
                intervalRange = 15.milliseconds..30.milliseconds,
                slaveLatency = 4,
                supervisionTimeout = 2000.milliseconds,
            )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `ConnectionParameters copy produces independent instance`() {
        val original =
            ConnectionParameters(
                intervalRange = 15.milliseconds..30.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 2000.milliseconds,
            )
        val copied = original.copy(slaveLatency = 4)
        assertEquals(0, original.slaveLatency)
        assertEquals(4, copied.slaveLatency)
        assertEquals(original.intervalRange, copied.intervalRange)
    }

    // --- ConnectionParameterUpdateResult ---

    @Test
    fun `ConnectionParameterUpdateResult stores and returns values`() {
        val result =
            ConnectionParameterUpdateResult(
                negotiatedInterval = 25.milliseconds,
                negotiatedLatency = 2,
                negotiatedSupervisionTimeout = 5000.milliseconds,
            )
        assertEquals(25.milliseconds, result.negotiatedInterval)
        assertEquals(2, result.negotiatedLatency)
        assertEquals(5000.milliseconds, result.negotiatedSupervisionTimeout)
    }

    @Test
    fun `ConnectionParameterUpdateResult equality works`() {
        val a = ConnectionParameterUpdateResult(30.milliseconds, 0, 4000.milliseconds)
        val b = ConnectionParameterUpdateResult(30.milliseconds, 0, 4000.milliseconds)
        assertEquals(a, b)
    }

    // --- FakePeripheral: requestConnectionParameterUpdate ---

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate returns negotiated values on fake by default`() =
        runTest {
            val peripheral = FakePeripheral {}
            peripheral.connect()
            val result =
                peripheral.requestConnectionParameterUpdate(
                    ConnectionParameters(
                        intervalRange = 15.milliseconds..30.milliseconds,
                        slaveLatency = 0,
                        supervisionTimeout = 2000.milliseconds,
                    ),
                )
            assertNotNull(result)
            assertEquals(30.milliseconds, result.negotiatedInterval)
            assertEquals(0, result.negotiatedLatency)
            assertEquals(2000.milliseconds, result.negotiatedSupervisionTimeout)
            peripheral.close()
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate throws when not connected`() =
        runTest {
            val peripheral = FakePeripheral {}
            try {
                peripheral.requestConnectionParameterUpdate(
                    ConnectionParameters(
                        intervalRange = 15.milliseconds..30.milliseconds,
                        slaveLatency = 0,
                        supervisionTimeout = 2000.milliseconds,
                    ),
                )
                fail("expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue("connected" in (e.message ?: "").lowercase())
            }
            peripheral.close()
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate throws after close`() =
        runTest {
            val peripheral = FakePeripheral {}
            peripheral.connect()
            peripheral.close()
            try {
                peripheral.requestConnectionParameterUpdate(
                    ConnectionParameters(
                        intervalRange = 15.milliseconds..30.milliseconds,
                        slaveLatency = 0,
                        supervisionTimeout = 2000.milliseconds,
                    ),
                )
                fail("expected IllegalStateException")
            } catch (e: IllegalStateException) {
                // closed check passes through FakeGattResponder.checkNotClosed()
            }
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate uses custom handler when configured`() =
        runTest {
            val expected =
                ConnectionParameterUpdateResult(
                    negotiatedInterval = 20.milliseconds,
                    negotiatedLatency = 3,
                    negotiatedSupervisionTimeout = 3000.milliseconds,
                )
            val peripheral =
                FakePeripheral {
                    onConnectionParameterUpdate { expected }
                }
            peripheral.connect()
            val result =
                peripheral.requestConnectionParameterUpdate(
                    ConnectionParameters(
                        intervalRange = 7.5.milliseconds..15.milliseconds,
                        slaveLatency = 0,
                        supervisionTimeout = 1000.milliseconds,
                    ),
                )
            assertEquals(expected, result)
            peripheral.close()
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate returns null when handler returns null`() =
        runTest {
            val peripheral =
                FakePeripheral {
                    onConnectionParameterUpdate { null }
                }
            peripheral.connect()
            val result =
                peripheral.requestConnectionParameterUpdate(
                    ConnectionParameters(
                        intervalRange = 15.milliseconds..30.milliseconds,
                        slaveLatency = 0,
                        supervisionTimeout = 2000.milliseconds,
                    ),
                )
            assertNull(result)
            peripheral.close()
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate forwards request params to handler`() =
        runTest {
            val requested =
                ConnectionParameters(
                    intervalRange = 50.milliseconds..100.milliseconds,
                    slaveLatency = 10,
                    supervisionTimeout = 10000.milliseconds,
                )
            var received: ConnectionParameters? = null
            val peripheral =
                FakePeripheral {
                    onConnectionParameterUpdate { params ->
                        received = params
                        ConnectionParameterUpdateResult(
                            negotiatedInterval = 75.milliseconds,
                            negotiatedLatency = 5,
                            negotiatedSupervisionTimeout = 8000.milliseconds,
                        )
                    }
                }
            peripheral.connect()
            peripheral.requestConnectionParameterUpdate(requested)
            assertNotNull(received)
            assertEquals(50.milliseconds, received!!.intervalRange.start)
            assertEquals(100.milliseconds, received!!.intervalRange.endInclusive)
            assertEquals(10, received!!.slaveLatency)
            assertEquals(10000.milliseconds, received!!.supervisionTimeout)
            peripheral.close()
        }

    @OptIn(ExperimentalBleApi::class)
    @Test
    fun `requestConnectionParameterUpdate default handler uses endInclusive for negotiatedInterval`() =
        runTest {
            val peripheral = FakePeripheral {}
            peripheral.connect()
            val result =
                peripheral.requestConnectionParameterUpdate(
                    ConnectionParameters(
                        intervalRange = 7.5.milliseconds..50.milliseconds,
                        slaveLatency = 2,
                        supervisionTimeout = 5000.milliseconds,
                    ),
                )
            assertNotNull(result)
            assertEquals(50.milliseconds, result.negotiatedInterval)
            assertEquals(2, result.negotiatedLatency)
            assertEquals(5000.milliseconds, result.negotiatedSupervisionTimeout)
            peripheral.close()
        }
}
