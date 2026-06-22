package com.atruedev.kmpble.connection

import com.atruedev.kmpble.testing.FakePeripheral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectionSubratingTest {
    @Test
    fun `requestConnectionSubrating returns Accepted with same parameters on fake`() =
        kotlinx.coroutines.test.runTest {
            val peripheral = FakePeripheral {}
            peripheral.connect()
            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 1,
                    continuationNumber = 4,
                    supervisionTimeout = 500,
                )
            val result = peripheral.requestConnectionSubrating(params)
            assertIs<ConnectionSubratingResult.Accepted>(result)
            assertEquals(params, result.parameters)
            peripheral.close()
        }

    @Test
    fun `requestConnectionSubrating throws when not connected on fake`() =
        kotlinx.coroutines.test.runTest {
            val peripheral = FakePeripheral {}
            try {
                peripheral.requestConnectionSubrating(
                    ConnectionSubratingParameters(
                        subrateFactor = 2,
                        subrateLatency = 0,
                        continuationNumber = 0,
                        supervisionTimeout = 100,
                    ),
                )
                kotlin.test.fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue("not connected" in e.message.orEmpty().lowercase())
            }
            peripheral.close()
        }

    @Test
    fun `parameters validation rejects out of range values`() {
        assertFailsWith<IllegalArgumentException>("subrateFactor 0") {
            ConnectionSubratingParameters(
                subrateFactor = 0,
                subrateLatency = 0,
                continuationNumber = 0,
                supervisionTimeout = 100,
            )
        }
        assertFailsWith<IllegalArgumentException>("subrateFactor 472") {
            ConnectionSubratingParameters(
                subrateFactor = 472,
                subrateLatency = 0,
                continuationNumber = 0,
                supervisionTimeout = 100,
            )
        }
        assertFailsWith<IllegalArgumentException>("subrateLatency 32") {
            ConnectionSubratingParameters(
                subrateFactor = 2,
                subrateLatency = 32,
                continuationNumber = 0,
                supervisionTimeout = 100,
            )
        }
        assertFailsWith<IllegalArgumentException>("continuationNumber 32") {
            ConnectionSubratingParameters(
                subrateFactor = 2,
                subrateLatency = 0,
                continuationNumber = 32,
                supervisionTimeout = 100,
            )
        }
        assertFailsWith<IllegalArgumentException>("supervisionTimeout 5") {
            ConnectionSubratingParameters(
                subrateFactor = 2,
                subrateLatency = 0,
                continuationNumber = 0,
                supervisionTimeout = 5,
            )
        }
        assertFailsWith<IllegalArgumentException>("supervisionTimeout 3205") {
            ConnectionSubratingParameters(
                subrateFactor = 2,
                subrateLatency = 0,
                continuationNumber = 0,
                supervisionTimeout = 3205,
            )
        }
    }

    @Test
    fun `parameters validation accepts boundary values`() {
        ConnectionSubratingParameters(
            subrateFactor = 1,
            subrateLatency = 0,
            continuationNumber = 0,
            supervisionTimeout = 10,
        )
        ConnectionSubratingParameters(
            subrateFactor = 471,
            subrateLatency = 31,
            continuationNumber = 31,
            supervisionTimeout = 3200,
        )
    }

    @Test
    fun `result NotSupported is a singleton`() {
        assertEquals(ConnectionSubratingResult.NotSupported, ConnectionSubratingResult.NotSupported)
    }

    @Test
    fun `result Rejected carries reason`() {
        val rejected = ConnectionSubratingResult.Rejected("test reason")
        assertEquals("test reason", rejected.reason)
    }
}
