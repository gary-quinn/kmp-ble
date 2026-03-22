package com.atruedev.kmpble.codec

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.testing.FakeGattServer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ServerCodecTest {

    private val device = Identifier("AA:BB:CC:DD:EE:01")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")

    @Test
    fun notifyEncodesValue() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device)

        server.notify(charUuid, device, "hello", TestStringEncoder)

        val records = server.getNotifications()
        assertEquals(1, records.size)
        assertEquals(charUuid, records[0].characteristicUuid)
        assertEquals(device, records[0].device)
        assertContentEquals("hello".encodeToByteArray(), records[0].data.toByteArray())
    }

    @Test
    fun notifyBroadcastEncodesValue() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device)

        server.notify(charUuid, null, "broadcast", TestStringEncoder)

        val records = server.getNotifications()
        assertEquals(1, records.size)
        assertContentEquals("broadcast".encodeToByteArray(), records[0].data.toByteArray())
    }

    @Test
    fun indicateEncodesValue() = runTest {
        val server = FakeGattServer()
        server.open()
        server.simulateConnection(device)

        server.indicate(charUuid, device, "ack-me", TestStringEncoder)

        val records = server.getIndications()
        assertEquals(1, records.size)
        assertEquals(charUuid, records[0].characteristicUuid)
        assertEquals(device, records[0].device)
        assertContentEquals("ack-me".encodeToByteArray(), records[0].data.toByteArray())
    }
}
