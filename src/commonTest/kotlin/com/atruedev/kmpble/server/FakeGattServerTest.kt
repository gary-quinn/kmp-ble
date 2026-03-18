package com.atruedev.kmpble.server

import com.atruedev.kmpble.Identifier
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

class FakeGattServerTest {

    private val device1 = Identifier("AA:BB:CC:DD:EE:01")
    private val device2 = Identifier("AA:BB:CC:DD:EE:02")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")

    @Test
    fun notify_records_are_captured() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device1)

        val data = byteArrayOf(0x01, 0x02, 0x03)
        server.notify(charUuid, device1, data)

        val records = server.getNotifications()
        assertEquals(1, records.size)
        assertEquals(charUuid, records[0].characteristicUuid)
        assertEquals(device1, records[0].device)
        assertTrue(data.contentEquals(records[0].data))
    }

    @Test
    fun indicate_records_are_captured() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device1)

        val data = byteArrayOf(0x04, 0x05)
        server.indicate(charUuid, device1, data)

        val records = server.getIndications()
        assertEquals(1, records.size)
        assertEquals(charUuid, records[0].characteristicUuid)
        assertEquals(device1, records[0].device)
        assertTrue(data.contentEquals(records[0].data))
    }

    @Test
    fun simulateConnection_updates_connections_flow() = runTest {
        val server = FakeGattServer()
        server.open()

        server.simulateConnection(device1, "Device1")
        assertEquals(1, server.connections.value.size)
        assertEquals(device1, server.connections.value[0].device)
        assertEquals("Device1", server.connections.value[0].name)

        server.simulateConnection(device2, "Device2")
        assertEquals(2, server.connections.value.size)
    }

    @Test
    fun simulateDisconnection_removes_from_connections() = runTest {
        val server = FakeGattServer()
        server.open()

        server.simulateConnection(device1, "Device1")
        server.simulateConnection(device2, "Device2")
        assertEquals(2, server.connections.value.size)

        server.simulateDisconnection(device1)
        assertEquals(1, server.connections.value.size)
        assertEquals(device2, server.connections.value[0].device)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun connectionEvents_emits_on_connect_and_disconnect() = runTest {
        val server = FakeGattServer()
        server.open()

        val events = mutableListOf<ServerConnectionEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
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
    }

    @Test
    fun notify_throws_NotOpen_before_open() = runTest {
        val server = FakeGattServer()

        assertFailsWith<ServerException.NotOpen> {
            server.notify(charUuid, device1, byteArrayOf(0x01))
        }
    }

    @Test
    fun indicate_throws_NotOpen_before_open() = runTest {
        val server = FakeGattServer()

        assertFailsWith<ServerException.NotOpen> {
            server.indicate(charUuid, device1, byteArrayOf(0x01))
        }
    }

    @Test
    fun notify_throws_DeviceNotConnected_for_unknown_device() = runTest {
        val server = FakeGattServer()
        server.open()

        assertFailsWith<ServerException.DeviceNotConnected> {
            server.notify(charUuid, device1, byteArrayOf(0x01))
        }
    }

    @Test
    fun indicate_throws_DeviceNotConnected_for_unknown_device() = runTest {
        val server = FakeGattServer()
        server.open()

        assertFailsWith<ServerException.DeviceNotConnected> {
            server.indicate(charUuid, device1, byteArrayOf(0x01))
        }
    }

    @Test
    fun close_clears_connections() = runTest {
        val server = FakeGattServer()
        server.open()

        server.simulateConnection(device1)
        server.simulateConnection(device2)
        assertEquals(2, server.connections.value.size)

        server.close()
        assertTrue(server.connections.value.isEmpty())
        assertFalse(server.isOpen)
    }

    @Test
    fun notify_with_null_device_is_captured() = runTest {
        val server = FakeGattServer()
        server.open()

        server.notify(charUuid, null, byteArrayOf(0x01))

        val records = server.getNotifications()
        assertEquals(1, records.size)
        assertEquals(null, records[0].device)
    }

    @Test
    fun clearNotifications_removes_all_records() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device1)
        server.simulateConnection(device2)

        server.notify(charUuid, device1, byteArrayOf(0x01))
        server.notify(charUuid, device2, byteArrayOf(0x02))
        assertEquals(2, server.getNotifications().size)

        server.clearNotifications()
        assertTrue(server.getNotifications().isEmpty())
    }

    @Test
    fun multiple_notify_calls_are_captured_in_order() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device1)
        server.simulateConnection(device2)

        server.notify(charUuid, device1, byteArrayOf(0x01))
        server.notify(charUuid, device2, byteArrayOf(0x02))
        server.notify(charUuid, device1, byteArrayOf(0x03))

        val records = server.getNotifications()
        assertEquals(3, records.size)
        assertTrue(byteArrayOf(0x01).contentEquals(records[0].data))
        assertTrue(byteArrayOf(0x02).contentEquals(records[1].data))
        assertTrue(byteArrayOf(0x03).contentEquals(records[2].data))
    }
}
