package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.testing.FakeDfuTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketWriterTest {

    @Test
    fun writesDataInMtuSizedChunks() = runTest {
        val transport = FakeDfuTransport(mtu = 10)
        val writer = PacketWriter(transport, prnInterval = 0)
        val data = ByteArray(25) { it.toByte() }

        writer.writeData(data)

        val chunks = transport.getDataLog()
        assertEquals(3, chunks.size)
        assertEquals(10, chunks[0].size)
        assertEquals(10, chunks[1].size)
        assertEquals(5, chunks[2].size)
    }

    @Test
    fun exactMtuMultiple() = runTest {
        val transport = FakeDfuTransport(mtu = 10)
        val writer = PacketWriter(transport, prnInterval = 0)
        val data = ByteArray(20) { it.toByte() }

        writer.writeData(data)

        assertEquals(2, transport.getDataLog().size)
    }

    @Test
    fun waitsForPrnNotification() = runTest {
        val transport = FakeDfuTransport(mtu = 5)
        val writer = PacketWriter(transport, prnInterval = 2)
        val data = ByteArray(20) { it.toByte() } // 4 packets

        val job = launch {
            writer.writeData(data)
        }

        // After 2 packets, writer waits for PRN notification
        // Send the notification to unblock
        transport.emitNotification(byteArrayOf(0x60, 0x03, 0x01, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        job.join()

        assertEquals(4, transport.getDataLog().size)
    }

    @Test
    fun reportsProgressViaCallback() = runTest {
        val transport = FakeDfuTransport(mtu = 10)
        val writer = PacketWriter(transport, prnInterval = 0)
        val data = ByteArray(25) { it.toByte() }

        val progressUpdates = mutableListOf<Int>()
        writer.writeData(data) { bytesSent -> progressUpdates.add(bytesSent) }

        assertEquals(listOf(10, 20, 25), progressUpdates)
    }

    @Test
    fun writesFromOffset() = runTest {
        val transport = FakeDfuTransport(mtu = 10)
        val writer = PacketWriter(transport, prnInterval = 0)
        val data = ByteArray(25) { it.toByte() }

        writer.writeData(data, offset = 10)

        val chunks = transport.getDataLog()
        assertEquals(2, chunks.size)
        assertEquals(10, chunks[0].size)
        assertEquals(5, chunks[1].size)
        assertEquals(10, chunks[0][0].toInt())
    }
}
