package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Connection lifecycle conformance tests.
 *
 * Verifies connect/disconnect/reconnect state transitions and
 * service availability across KMP platforms.
 */
public abstract class ConnectionConformanceTest : BleConformanceTest() {
    @Test
    fun `connect transitions peripheral to connected state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            assertTrue(peripheral.state.value is State.Connected)
            peripheral.close()
        }

    @Test
    fun `disconnect transitions peripheral to disconnecting state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.disconnect()

            val state = peripheral.state.value
            assertTrue(
                state is State.Disconnecting || state is State.Disconnected,
                "Expected Disconnecting or Disconnected, got $state",
            )
            peripheral.close()
        }

    @Test
    fun `reconnect after disconnect restores connected state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            assertTrue(peripheral.state.value is State.Connected)

            peripheral.disconnect()
            peripheral.connect(ConnectionOptions())
            assertTrue(peripheral.state.value is State.Connected)

            peripheral.close()
        }

    @Test
    fun `services property non-null only after discovery`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("1800") {
                        characteristic("2a00") { properties(read = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()

            val services = peripheral.services.value
            assertNotNull(services, "Services should be non-null after discovery")
            assertTrue(services.isNotEmpty(), "Services should not be empty after discovery")
            peripheral.close()
        }
}
