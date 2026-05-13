@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.codec

import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakeL2capListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val TestStringCodec = object : BleCodec<String> {
    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(data: ByteArray): String = data.decodeToString()
}

private val FailingStringCodec = object : BleCodec<String> {
    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(data: ByteArray): String = throw IllegalArgumentException("decode failed: ${data.size} bytes")
}

class TypedL2capChannelTest {

    @Test
    fun framedConnectionsEmitsTypedWrappersForAcceptedChannels() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val raw = FakeL2capChannel(psm = 0x80, mtu = 512)

        val collected = mutableListOf<TypedL2capChannel<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            listener.framedConnections(TestStringCodec).take(1).toList(collected)
        }

        listener.simulateIncoming(raw)

        assertEquals(1, collected.size)
        assertSame(raw, collected[0].channel)
        job.cancel()
    }

    @Test
    fun typedIncomingDecodesFramedBytes() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val raw = FakeL2capChannel(psm = 0x80)
        val framer = LengthPrefixFramer()

        val received = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            val typed = listener.framedConnections(TestStringCodec, framer).first()
            typed.incoming.collect { received.add(it) }
        }

        listener.simulateIncoming(raw)
        raw.emitIncoming(framer.frame("alpha".encodeToByteArray()))
        raw.emitIncoming(framer.frame("beta".encodeToByteArray()))
        raw.emitIncoming(framer.frame("gamma".encodeToByteArray()))

        assertEquals(listOf("alpha", "beta", "gamma"), received)
        job.cancel()
    }

    @Test
    fun typedWriteEncodesAndFramesBytes() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val raw = FakeL2capChannel(psm = 0x80)
        val framer = LengthPrefixFramer()

        val acceptJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            val typed = listener.framedConnections(TestStringCodec, framer).first()
            typed.write("ping")
        }
        listener.simulateIncoming(raw)
        acceptJob.join()

        val written = raw.getWrittenData()
        assertEquals(1, written.size)
        assertContentEquals(framer.frame("ping".encodeToByteArray()), written[0])
    }

    @Test
    fun typedChannelForwardsUnderlyingProperties() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val raw = FakeL2capChannel(psm = 0x42, mtu = 1024)

        val captured = mutableListOf<TypedL2capChannel<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            listener.framedConnections(TestStringCodec).take(1).toList(captured)
        }
        listener.simulateIncoming(raw)

        val wrapper = captured.single()
        assertEquals(0x42, wrapper.psm)
        assertEquals(1024, wrapper.mtu)
        assertTrue(wrapper.isOpen)
        job.cancel()
    }

    @Test
    fun typedChannelCloseClosesUnderlying() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val raw = FakeL2capChannel(psm = 0x80)

        val captured = mutableListOf<TypedL2capChannel<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            listener.framedConnections(TestStringCodec).take(1).toList(captured)
        }
        listener.simulateIncoming(raw)

        val wrapper = captured.single()
        assertTrue(raw.isOpen)
        wrapper.close()
        assertFalse(raw.isOpen)
        job.cancel()
    }

    @Test
    fun decoderFailuresRouteToCallback() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val raw = FakeL2capChannel(psm = 0x80)
        val framer = LengthPrefixFramer()
        val failed = mutableListOf<ByteArray>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            val typed = listener
                .framedConnections(FailingStringCodec, framer, onDecodeFailure = { failed.add(it) })
                .first()
            typed.incoming.collect { }
        }

        listener.simulateIncoming(raw)
        raw.emitIncoming(framer.frame("bad-frame".encodeToByteArray()))

        assertEquals(1, failed.size)
        assertContentEquals("bad-frame".encodeToByteArray(), failed[0])
        job.cancel()
    }

    @Test
    fun multipleAcceptedChannelsHaveIndependentUnframerState() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x80)
        listener.open()
        val channelA = FakeL2capChannel(psm = 0x80)
        val channelB = FakeL2capChannel(psm = 0x80)
        val framer = LengthPrefixFramer()

        val receivedA = mutableListOf<String>()
        val receivedB = mutableListOf<String>()
        val typedFlow = listener.framedConnections(TestStringCodec, framer)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            typedFlow.take(2).collect { typed ->
                val sink = if (typed.channel === channelA) receivedA else receivedB
                launch { typed.incoming.collect { sink.add(it) } }
            }
        }

        listener.simulateIncoming(channelA)
        listener.simulateIncoming(channelB)

        val framedHello = framer.frame("hello".encodeToByteArray())
        channelA.emitIncoming(framedHello.copyOfRange(0, 3))
        channelB.emitIncoming(framer.frame("world".encodeToByteArray()))
        channelA.emitIncoming(framedHello.copyOfRange(3, framedHello.size))

        assertEquals(listOf("hello"), receivedA)
        assertEquals(listOf("world"), receivedB)
        job.cancel()
    }
}
