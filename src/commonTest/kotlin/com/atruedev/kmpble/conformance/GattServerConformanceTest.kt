package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.server.GattServerBuilder
import com.atruedev.kmpble.server.ServerConnectionEvent
import com.atruedev.kmpble.server.ServerException
import com.atruedev.kmpble.testing.FakeGattServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * GATT server conformance tests.
 *
 * Verifies server lifecycle, connection management, notify/indicate
 * operations, and error handling across KMP platforms.
 *
 * Platform-specific runners (JvmGattServerConformanceTest,
 * IosGattServerConformanceTest) extend this class and run all
 * inherited tests with the platform's [FakeGattServer] variant.
 */
public abstract class GattServerConformanceTest {
    private val device1 = Identifier("AA:BB:CC:DD:EE:01")
    private val device2 = Identifier("AA:BB:CC:DD:EE:02")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")

    /**
     * Factory for GATT server test doubles. Override to inject platform behavior.
     *
     * Returns [FakeGattServer] so tests can use [FakeGattServer.simulateConnection]
     * and [FakeGattServer.simulateDisconnection] helpers.
     */
    protected open fun buildServer(block: GattServerBuilder.() -> Unit = {}): FakeGattServer = FakeGattServer()

    // --- Lifecycle ---

    @Test
    fun open_and_close_lifecycle() =
        runTest {
            val server = buildServer()
            assertFalse(server.connections.value.isNotEmpty(), "No connections before open")

            server.open()
            assertTrue(server.connections.value.isEmpty(), "Still no connections after open")

            server.close()
            // After close, operations should fail
            assertFailsWith<ServerException.NotOpen> {
                server.notify(charUuid, device1, BleData(byteArrayOf(0x01)))
            }
        }

    @Test
    fun notify_throws_NotOpen_before_open() =
        runTest {
            val server = buildServer()

            assertFailsWith<ServerException.NotOpen> {
                server.notify(charUuid, device1, BleData(byteArrayOf(0x01)))
            }
        }

    @Test
    fun indicate_throws_NotOpen_before_open() =
        runTest {
            val server = buildServer()

            assertFailsWith<ServerException.NotOpen> {
                server.indicate(charUuid, device1, BleData(byteArrayOf(0x01)))
            }
        }

    @Test
    fun operations_throw_NotOpen_after_close() =
        runTest {
            val server = buildServer()
            server.open()
            server.close()

            assertFailsWith<ServerException.NotOpen> {
                server.notify(charUuid, device1, BleData(byteArrayOf(0x01)))
            }
            assertFailsWith<ServerException.NotOpen> {
                server.indicate(charUuid, device1, BleData(byteArrayOf(0x01)))
            }
        }

    // --- Connection management ---

    @Test
    fun connections_empty_initially() =
        runTest {
            val server = buildServer()
            server.open()

            assertTrue(server.connections.value.isEmpty())
            server.close()
        }

    @Test
    fun single_connection_appears_in_connections() =
        runTest {
            val server = buildServer()
            server.open()

            server.simulateConnection(device1, "TestDevice")
            assertEquals(1, server.connections.value.size)
            assertEquals(device1, server.connections.value[0].device)
            assertEquals("TestDevice", server.connections.value[0].name)

            server.close()
        }

    @Test
    fun multiple_connections_accumulate() =
        runTest {
            val server = buildServer()
            server.open()

            server.simulateConnection(device1)
            server.simulateConnection(device2)
            assertEquals(2, server.connections.value.size)

            server.close()
        }

    @Test
    fun disconnection_removes_device_from_connections() =
        runTest {
            val server = buildServer()
            server.open()

            server.simulateConnection(device1)
            server.simulateConnection(device2)
            assertEquals(2, server.connections.value.size)

            server.simulateDisconnection(device1)
            assertEquals(1, server.connections.value.size)
            assertEquals(device2, server.connections.value[0].device)

            server.close()
        }

    @Test
    fun close_clears_all_connections() =
        runTest {
            val server = buildServer()
            server.open()

            server.simulateConnection(device1)
            server.simulateConnection(device2)
            assertEquals(2, server.connections.value.size)

            server.close()
            assertTrue(server.connections.value.isEmpty())
        }

    // --- Connection events ---

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun connectionEvents_emits_on_connect_and_disconnect() =
        runTest {
            val server = buildServer()
            server.open()

            val events = mutableListOf<ServerConnectionEvent>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    server.connectionEvents.collect { events.add(it) }
                }

            server.simulateConnection(device1)
            server.simulateDisconnection(device1)

            assertEquals(2, events.size)
            assertIs<ServerConnectionEvent.Connected>(events[0])
            assertEquals(device1, (events[0] as ServerConnectionEvent.Connected).device)
            assertIs<ServerConnectionEvent.Disconnected>(events[1])
            assertEquals(device1, (events[1] as ServerConnectionEvent.Disconnected).device)

            job.cancel()
            server.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun connectionEvents_emits_for_multiple_devices() =
        runTest {
            val server = buildServer()
            server.open()

            val events = mutableListOf<ServerConnectionEvent>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    server.connectionEvents.collect { events.add(it) }
                }

            server.simulateConnection(device1)
            server.simulateConnection(device2)
            server.simulateDisconnection(device1)

            assertEquals(3, events.size)
            assertIs<ServerConnectionEvent.Connected>(events[0])
            assertIs<ServerConnectionEvent.Connected>(events[1])
            assertIs<ServerConnectionEvent.Disconnected>(events[2])

            job.cancel()
            server.close()
        }

    // --- Notify ---

    @Test
    fun notify_throws_DeviceNotConnected_for_unknown_device() =
        runTest {
            val server = buildServer()
            server.open()

            assertFailsWith<ServerException.DeviceNotConnected> {
                server.notify(charUuid, device1, BleData(byteArrayOf(0x01)))
            }

            server.close()
        }

    @Test
    fun notify_with_null_device_succeeds() =
        runTest {
            val server = buildServer()
            server.open()

            // Broadcast notify (null device) should succeed without explicit connection
            server.notify(charUuid, null, BleData(byteArrayOf(0x01)))

            server.close()
        }

    @Test
    fun notify_with_connected_device_succeeds() =
        runTest {
            val server = buildServer()
            server.open()
            server.simulateConnection(device1)

            // Should not throw
            server.notify(charUuid, device1, BleData(byteArrayOf(0x01)))

            server.close()
        }

    // --- Indicate ---

    @Test
    fun indicate_throws_DeviceNotConnected_for_unknown_device() =
        runTest {
            val server = buildServer()
            server.open()

            assertFailsWith<ServerException.DeviceNotConnected> {
                server.indicate(charUuid, device1, BleData(byteArrayOf(0x01)))
            }

            server.close()
        }

    @Test
    fun indicate_with_connected_device_succeeds() =
        runTest {
            val server = buildServer()
            server.open()
            server.simulateConnection(device1)

            // Should not throw
            server.indicate(charUuid, device1, BleData(byteArrayOf(0x01)))

            server.close()
        }

    // --- Notification/indication capture ---

    @Test
    fun notify_captures_are_recorded() =
        runTest {
            val server = buildServer()
            server.open()
            server.simulateConnection(device1)

            val data = BleData(byteArrayOf(0x01, 0x02, 0x03))
            server.notify(charUuid, device1, data)

            val records = server.getNotifications()
            assertEquals(1, records.size)
            assertEquals(charUuid, records[0].characteristicUuid)
            assertEquals(device1, records[0].device)
            assertEquals(data, records[0].data)

            server.close()
        }

    @Test
    fun indicate_captures_are_recorded() =
        runTest {
            val server = buildServer()
            server.open()
            server.simulateConnection(device1)

            val data = BleData(byteArrayOf(0x04, 0x05))
            server.indicate(charUuid, device1, data)

            val records = server.getIndications()
            assertEquals(1, records.size)
            assertEquals(charUuid, records[0].characteristicUuid)
            assertEquals(device1, records[0].device)
            assertEquals(data, records[0].data)

            server.close()
        }

    @Test
    fun clearNotifications_removes_all_records() =
        runTest {
            val server = buildServer()
            server.open()
            server.simulateConnection(device1)
            server.simulateConnection(device2)

            server.notify(charUuid, device1, BleData(byteArrayOf(0x01)))
            server.notify(charUuid, device2, BleData(byteArrayOf(0x02)))
            assertEquals(2, server.getNotifications().size)

            server.clearNotifications()
            assertTrue(server.getNotifications().isEmpty())

            server.close()
        }

    // --- Broadcast notify ---

    @Test
    fun broadcast_notify_with_null_device_after_close_throws() =
        runTest {
            val server = buildServer()
            server.open()
            server.close()

            assertFailsWith<ServerException.NotOpen> {
                server.notify(charUuid, null, BleData(byteArrayOf(0x01)))
            }
        }

    // --- Disconnect non-connected device ---

    @Test
    fun disconnecting_non_connected_device_is_no_op() =
        runTest {
            val server = buildServer()
            server.open()

            server.simulateConnection(device1)
            server.simulateDisconnection(device2) // Not connected
            assertEquals(1, server.connections.value.size, "device1 should still be connected")

            server.close()
        }
}
