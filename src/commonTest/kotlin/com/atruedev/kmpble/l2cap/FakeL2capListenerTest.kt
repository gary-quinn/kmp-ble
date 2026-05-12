@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.testing.FakeL2capListener
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeL2capListenerTest {

    @Test
    fun openSetsIsOpenAndPsm() = runTest {
        val listener = FakeL2capListener(assignedPsm = 0x42)
        assertFalse(listener.isOpen.value)
        assertEquals(0, listener.psm)

        listener.open(secure = true)

        assertTrue(listener.isOpen.value)
        assertEquals(0x42, listener.psm)
    }

    @Test
    fun openWhenAlreadyOpenThrows() = runTest {
        val listener = FakeL2capListener()
        listener.open()
        assertFailsWith<L2capException.InvalidState> { listener.open() }
    }

    @Test
    fun openAfterCloseThrows() = runTest {
        val listener = FakeL2capListener()
        listener.open()
        listener.close()
        assertFailsWith<L2capException.InvalidState> { listener.open() }
    }

    @Test
    fun simulateIncomingEmitsChannels() = runTest {
        val listener = FakeL2capListener()
        listener.open()
        val channel = StubChannel(psm = listener.psm)

        val collected = async { listener.incoming.take(1).toList() }
        runCurrent()
        listener.simulateIncoming(channel)
        val received = collected.await()

        assertEquals(1, received.size)
        assertEquals(listener.psm, received[0].psm)
    }

    @Test
    fun simulateIncomingBeforeOpenThrows() {
        val listener = FakeL2capListener()
        assertFailsWith<IllegalStateException> {
            listener.simulateIncoming(StubChannel(psm = 1))
        }
    }

    @Test
    fun closeIsIdempotent() = runTest {
        val listener = FakeL2capListener()
        listener.open()
        listener.close()
        listener.close()
        assertFalse(listener.isOpen.value)
    }
}

private class StubChannel(override val psm: Int) : L2capChannel {
    override val mtu: Int = 2048
    override val isOpen: Boolean = true
    override val incoming: Flow<ByteArray> = flowOf()
    override suspend fun write(data: ByteArray) {}
    override fun close() {}
}
