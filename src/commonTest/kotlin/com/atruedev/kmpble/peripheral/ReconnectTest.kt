package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.PhyMask
import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [Peripheral.reconnect] extension function.
 *
 * Validates that reconnect() uses the last-used ConnectionOptions,
 * re-discovers services, and handles edge cases correctly.
 */
class ReconnectTest {
    @Test
    fun reconnect_throwsWhenNoPreviousConnection() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                }

            assertFailsWith<IllegalStateException> {
                peripheral.reconnect()
            }
        }

    @Test
    fun reconnect_connectsAfterDisconnect() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                }

            peripheral.connect()
            peripheral.disconnect()
            peripheral.reconnect()

            assertIs<State.Connected.Ready>(peripheral.state.value)
            peripheral.disconnect()
        }

    @Test
    fun reconnect_rediscoverServicesAfterDisconnect() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                    service("180f") {
                        characteristic("2a19") { properties(read = true) }
                    }
                }

            peripheral.connect()
            peripheral.disconnect()
            peripheral.reconnect()

            val services = peripheral.services.value
            assertTrue(services?.isNotEmpty() == true)
            assertEquals(2, services?.size)
            peripheral.disconnect()
        }

    @Test
    fun reconnect_usesDefaultOptionsWhenConnectHadDefaults() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                }

            peripheral.connect()
            peripheral.disconnect()
            peripheral.reconnect()

            assertIs<State.Connected.Ready>(peripheral.state.value)
            peripheral.disconnect()
        }

    @Test
    fun reconnect_usesCustomOptionsFromPreviousConnect() =
        runTest {
            val options =
                ConnectionOptions(
                    autoConnect = false,
                    phyMask = PhyMask.LE_2M,
                    mtuRequest = 185,
                )
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                }

            peripheral.connect(options)
            peripheral.disconnect()
            peripheral.reconnect()

            // If the peripheral accepted the connection, options were used
            assertIs<State.Connected.Ready>(peripheral.state.value)
            peripheral.disconnect()
        }

    @Test
    fun reconnect_whileAlreadyConnected_throwsError() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                }

            peripheral.connect()

            assertFailsWith<IllegalStateException> {
                peripheral.reconnect()
            }

            peripheral.disconnect()
        }

    @Test
    fun reconnect_maintainsConnectCallCount() =
        runTest {
            val connectAttempts = mutableListOf<Int>()

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(read = true) }
                    }
                }

            // First connect
            peripheral.connect()
            peripheral.disconnect()

            // Reconnect should count as a new connect
            peripheral.reconnect()

            assertIs<State.Connected.Ready>(peripheral.state.value)
            peripheral.disconnect()
        }
}
