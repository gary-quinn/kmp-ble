@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.l2cap

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidL2capChannelTest {
    private fun createChannel(
        socket: FakeL2capSocket = FakeL2capSocket(),
        psm: Int = 0x25,
    ): Pair<AndroidL2capChannel, FakeL2capSocket> {
        val channel = AndroidL2capChannel(socket, psm, kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()))
        return channel to socket
    }

    // =========================================================================
    // Basic lifecycle
    // =========================================================================

    @Test
    fun `channel is open after creation`() {
        val (channel, _) = createChannel()
        assertTrue(channel.isOpen)
        channel.close()
    }

    @Test
    fun `psm is set correctly`() {
        val (channel, _) = createChannel(psm = 0x42)
        assertEquals(0x42, channel.psm)
        channel.close()
    }

    @Test
    fun `isOpen returns false after close`() {
        val (channel, _) = createChannel()
        assertTrue(channel.isOpen)
        channel.close()
        assertFalse(channel.isOpen)
    }

    @Test
    fun `close is idempotent`() {
        val (channel, _) = createChannel()
        channel.close()
        channel.close()
        channel.close()
        assertFalse(channel.isOpen)
    }

    // =========================================================================
    // MTU
    // =========================================================================

    @Test
    fun `mtu returns maxTransmitPacketSize when above default`() {
        val socket = FakeL2capSocket(maxTransmitPacketSize = 2048)
        val (channel, _) = createChannel(socket)
        assertEquals(2048, channel.mtu)
        channel.close()
    }

    @Test
    fun `mtu floors at default when maxTransmitPacketSize is small`() {
        val socket = FakeL2capSocket(maxTransmitPacketSize = 100)
        val (channel, _) = createChannel(socket)
        assertEquals(AndroidL2capChannel.DEFAULT_MTU, channel.mtu)
        channel.close()
    }

    @Test
    fun `mtu returns default value`() {
        val socket = FakeL2capSocket(maxTransmitPacketSize = AndroidL2capChannel.DEFAULT_MTU)
        val (channel, _) = createChannel(socket)
        assertEquals(672, channel.mtu)
        channel.close()
    }

    // =========================================================================
    // Write
    // =========================================================================

    @Test
    fun `write sends data through output stream`() =
        runTest {
            val (channel, socket) = createChannel()
            val data = byteArrayOf(0x01, 0x02, 0x03)

            channel.write(data)

            val captured = ByteArray(3)
            val bytesRead = socket.localCapture.read(captured)
            assertEquals(3, bytesRead)
            assertContentEquals(data, captured)
            channel.close()
        }

    @Test
    fun `write throws ChannelClosed after close`() =
        runTest {
            val (channel, _) = createChannel()
            channel.close()

            assertFailsWith<L2capException.ChannelClosed> {
                channel.write(byteArrayOf(0x01, 0x02))
            }
        }

    @Test
    fun `write throws ChannelClosed when socket is disconnected`() =
        runTest {
            val (channel, socket) = createChannel()
            socket.simulateDisconnect()

            assertFailsWith<L2capException.ChannelClosed> {
                channel.write(byteArrayOf(0x01))
            }
            channel.close()
        }

    @Test
    fun `write throws WriteFailed when output stream is closed`() =
        runTest {
            val (channel, socket) = createChannel()
            socket.localCapture.close()

            assertFailsWith<L2capException.WriteFailed> {
                channel.write(byteArrayOf(0x01))
            }
            channel.close()
        }

    // =========================================================================
    // Read loop (incoming flow)
    // =========================================================================

    @Test
    fun `incoming flow receives data from remote`() =
        runTest {
            val (channel, socket) = createChannel()
            val received = mutableListOf<ByteArray>()

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    channel.incoming.collect { received.add(it) }
                }

            socket.remoteOutput.write(byteArrayOf(0x0A, 0x0B))
            socket.remoteOutput.flush()

            awaitCondition { received.isNotEmpty() }

            assertContentEquals(byteArrayOf(0x0A, 0x0B), received[0])

            channel.close()
            collectJob.join()
        }

    @Test
    fun `incoming flow receives multiple packets`() =
        runTest {
            val (channel, socket) = createChannel()
            val received = mutableListOf<ByteArray>()

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    channel.incoming.collect { received.add(it) }
                }

            socket.remoteOutput.write(byteArrayOf(0x01))
            socket.remoteOutput.flush()
            awaitCondition { received.size >= 1 }

            socket.remoteOutput.write(byteArrayOf(0x02))
            socket.remoteOutput.flush()
            awaitCondition { received.size >= 2 }

            assertContentEquals(byteArrayOf(0x01), received[0])
            assertContentEquals(byteArrayOf(0x02), received[1])

            channel.close()
            collectJob.join()
        }

    @Test
    fun `incoming flow completes when channel is closed`() =
        runTest {
            val (channel, _) = createChannel()
            var flowCompleted = false

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    channel.incoming.collect { }
                    flowCompleted = true
                }

            channel.close()
            collectJob.join()

            assertTrue(flowCompleted, "Flow should complete after close")
        }

    @Test
    fun `incoming flow completes on remote EOF`() =
        runTest {
            val (channel, socket) = createChannel()
            var flowCompleted = false

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    channel.incoming.collect { }
                    flowCompleted = true
                }

            socket.simulateRemoteClose()
            collectJob.join()

            assertTrue(flowCompleted, "Flow should complete on remote EOF")
        }

    // =========================================================================
    // awaitClosed
    // =========================================================================

    @Test
    fun `awaitClosed completes when channel is closed`() =
        runTest {
            val (channel, _) = createChannel()
            var awaited = false

            val awaitJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    channel.awaitClosed()
                    awaited = true
                }

            assertFalse(awaited, "Should not have completed yet")
            channel.close()
            awaitJob.join()
            assertTrue(awaited, "awaitClosed should complete after close")
        }

    @Test
    fun `awaitClosed completes on remote disconnect`() =
        runTest {
            val (channel, socket) = createChannel()
            var awaited = false

            val awaitJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    channel.awaitClosed()
                    awaited = true
                }

            socket.simulateRemoteClose()
            awaitJob.join()
            assertTrue(awaited, "awaitClosed should complete on remote close")
        }

    // =========================================================================
    // Socket disconnect mid-read
    // =========================================================================

    @Test
    fun `channel becomes not open after remote disconnect`() =
        runTest {
            val (channel, socket) = createChannel()
            assertTrue(channel.isOpen)

            socket.simulateRemoteClose()
            // Wait for read loop to detect EOF
            channel.awaitClosed()

            assertFalse(channel.isOpen)
        }

    @Test
    fun `write fails after remote disconnect closes channel`() =
        runTest {
            val (channel, socket) = createChannel()
            socket.simulateRemoteClose()
            channel.awaitClosed()

            assertFailsWith<L2capException.ChannelClosed> {
                channel.write(byteArrayOf(0x01))
            }
        }

    private suspend fun awaitCondition(
        timeoutMs: Long = 2_000,
        intervalMs: Long = 10,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within ${timeoutMs}ms" }
            kotlinx.coroutines.delay(intervalMs)
        }
    }
}
