package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * LE Connection Subrating integration and conformance tests.
 *
 * Verifies that subrating requests behave correctly across platforms:
 * - Parameters are validated according to BT Core Spec v5.3.
 * - Results carry negotiated values or proper error states.
 * - Platform-specific NotSupported behavior is testable.
 *
 * Android returns [ConnectionSubratingResult.NotSupported] at runtime (compileSdk 35+ pending).
 * iOS returns [ConnectionSubratingResult.NotSupported] (CoreBluetooth handles internally).
 * FakePeripheral returns [ConnectionSubratingResult.Accepted] with same parameters.
 */
public abstract class ConnectionSubratingConformanceTest : BleConformanceTest() {
    @Test
    fun `subrating request returns Accepted with negotiated parameters on fake`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

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
    fun `subrating request throws when peripheral is not connected`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 0,
                    continuationNumber = 0,
                    supervisionTimeout = 100,
                )

            try {
                peripheral.requestConnectionSubrating(params)
                throw AssertionError("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(
                    "not connected" in e.message.orEmpty().lowercase(),
                    "Error message should mention connection state, got: ${e.message}",
                )
            }

            peripheral.close()
        }

    @Test
    fun `subrating request throws when peripheral is disconnected`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.disconnect()

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 0,
                    continuationNumber = 0,
                    supervisionTimeout = 100,
                )

            try {
                peripheral.requestConnectionSubrating(params)
                throw AssertionError("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(
                    "not connected" in e.message.orEmpty().lowercase(),
                    "Error message should mention connection state, got: ${e.message}",
                )
            }

            peripheral.close()
        }

    @Test
    fun `subrating boundary parameters at spec minimums`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 1,
                    subrateLatency = 0,
                    continuationNumber = 0,
                    supervisionTimeout = 10,
                )

            val result = peripheral.requestConnectionSubrating(params)

            assertIs<ConnectionSubratingResult.Accepted>(result)
            assertEquals(params, result.parameters)

            peripheral.close()
        }

    @Test
    fun `subrating boundary parameters at spec maximums`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 471,
                    subrateLatency = 31,
                    continuationNumber = 31,
                    supervisionTimeout = 3200,
                )

            val result = peripheral.requestConnectionSubrating(params)

            assertIs<ConnectionSubratingResult.Accepted>(result)
            assertEquals(params, result.parameters)

            peripheral.close()
        }

    @Test
    fun `multiple subrating requests can be issued after reconnect`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            val firstParams =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 1,
                    continuationNumber = 4,
                    supervisionTimeout = 500,
                )

            val firstResult = peripheral.requestConnectionSubrating(firstParams)
            assertIs<ConnectionSubratingResult.Accepted>(firstResult)
            assertEquals(firstParams, firstResult.parameters)

            val secondParams =
                ConnectionSubratingParameters(
                    subrateFactor = 4,
                    subrateLatency = 2,
                    continuationNumber = 8,
                    supervisionTimeout = 1000,
                )

            val secondResult = peripheral.requestConnectionSubrating(secondParams)
            assertIs<ConnectionSubratingResult.Accepted>(secondResult)
            assertEquals(secondParams, secondResult.parameters)

            peripheral.disconnect()
            peripheral.connect(ConnectionOptions())

            val afterReconnectParams =
                ConnectionSubratingParameters(
                    subrateFactor = 3,
                    subrateLatency = 0,
                    continuationNumber = 2,
                    supervisionTimeout = 300,
                )

            val afterReconnectResult = peripheral.requestConnectionSubrating(afterReconnectParams)
            assertIs<ConnectionSubratingResult.Accepted>(afterReconnectResult)
            assertEquals(afterReconnectParams, afterReconnectResult.parameters)

            peripheral.close()
        }

    @Test
    fun `subrating does not interfere with GATT operations`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(notify = true, read = true)
                            onRead { byteArrayOf(0x06, 0x42, 0x00) }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())

            val char =
                peripheral.services.value!!
                    .first()
                    .characteristics
                    .first()

            val before = peripheral.read(char)
            assertEquals(0x42.toByte(), before[1])

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 0,
                    continuationNumber = 0,
                    supervisionTimeout = 200,
                )
            val result = peripheral.requestConnectionSubrating(params)
            assertIs<ConnectionSubratingResult.Accepted>(result)

            val after = peripheral.read(char)
            assertEquals(0x42.toByte(), after[1])

            peripheral.close()
        }

    @Test
    fun `subrating throws when peripheral is closed`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.close()

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 0,
                    continuationNumber = 0,
                    supervisionTimeout = 100,
                )

            try {
                peripheral.requestConnectionSubrating(params)
                throw AssertionError("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(
                    "closed" in e.message.orEmpty().lowercase(),
                    "Error message should mention closed state, got: ${e.message}",
                )
            }
        }

    @Test
    fun `Accepted result carries correct parameter types`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 10,
                    subrateLatency = 5,
                    continuationNumber = 3,
                    supervisionTimeout = 800,
                )

            val result = peripheral.requestConnectionSubrating(params)

            assertIs<ConnectionSubratingResult.Accepted>(result)
            assertEquals(10, result.parameters.subrateFactor)
            assertEquals(5, result.parameters.subrateLatency)
            assertEquals(3, result.parameters.continuationNumber)
            assertEquals(800, result.parameters.supervisionTimeout)

            peripheral.close()
        }

    @Test
    fun `NotSupported result is a singleton data object`() {
        val instance1 = ConnectionSubratingResult.NotSupported
        val instance2 = ConnectionSubratingResult.NotSupported
        assertEquals(instance1, instance2, "NotSupported must be a singleton")
        assertIs<ConnectionSubratingResult.NotSupported>(instance1)
    }

    @Test
    fun `Rejected result carries error reason`() {
        val withReason = ConnectionSubratingResult.Rejected("peripheral rejected")
        assertIs<ConnectionSubratingResult.Rejected>(withReason)
        assertEquals("peripheral rejected", withReason.reason)

        val withoutReason = ConnectionSubratingResult.Rejected()
        assertIs<ConnectionSubratingResult.Rejected>(withoutReason)
        assertEquals(null, withoutReason.reason)
    }

    @Test
    fun `subrating preserves connection state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            assertTrue(peripheral.state.value is State.Connected)

            val params =
                ConnectionSubratingParameters(
                    subrateFactor = 2,
                    subrateLatency = 0,
                    continuationNumber = 0,
                    supervisionTimeout = 200,
                )

            peripheral.requestConnectionSubrating(params)

            assertTrue(peripheral.state.value is State.Connected)

            peripheral.close()
        }
}
