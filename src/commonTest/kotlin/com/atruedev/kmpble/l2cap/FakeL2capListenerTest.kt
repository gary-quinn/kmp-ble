package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capChannelState
import com.atruedev.kmpble.testing.FakeL2capListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeL2capListenerTest {
    @Test
    fun openSetsIsOpenAndPsm() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x42)
            assertFalse(listener.isOpen.value)
            assertEquals(0, listener.psm)

            listener.open(secure = true)

            assertTrue(listener.isOpen.value)
            assertEquals(0x42, listener.psm)
        }

    @Test
    fun openWhenAlreadyOpenThrows() =
        runTest {
            val listener = FakeL2capListener()
            listener.open()
            assertFailsWith<L2capException.InvalidState> { listener.open() }
        }

    @Test
    fun openAfterCloseThrows() =
        runTest {
            val listener = FakeL2capListener()
            listener.open()
            listener.close()
            assertFailsWith<L2capException.InvalidState> { listener.open() }
        }

    @Test
    fun simulateIncomingEmitsChannels() =
        runTest {
            val listener = FakeL2capListener()
            listener.open()
            val channel = StubChannel(psm = listener.psm)

            val subscribed = CompletableDeferred<Unit>()
            val collected =
                async {
                    listener.incoming
                        .onSubscription { subscribed.complete(Unit) }
                        .take(1)
                        .toList()
                }
            subscribed.await()
            listener.simulateIncoming(channel)
            val received = collected.await()

            assertEquals(1, received.size)
            assertEquals(listener.psm, received[0].psm)
        }

    @Test
    fun simulateIncomingBeforeOpenThrows() =
        runTest {
            val listener = FakeL2capListener()
            assertFailsWith<IllegalStateException> {
                listener.simulateIncoming(StubChannel(psm = 1))
            }
        }

    @Test
    fun closeIsIdempotent() =
        runTest {
            val listener = FakeL2capListener()
            listener.open()
            listener.close()
            listener.close()
            assertFalse(listener.isOpen.value)
        }
}

private class StubChannel(
    override val psm: Int,
) : L2capChannel {
    override val mtu: Int = 2048
    override val isOpen: Boolean = true
    override val state = MutableStateFlow(L2capChannelState.Open).asStateFlow()
    override val errors: Flow<com.atruedev.kmpble.l2cap.L2capChannelError> = emptyFlow()
    override val incoming: Flow<ByteArray> = flowOf()

    override suspend fun write(data: ByteArray) {}

    override fun close() {}

    override suspend fun close(graceful: Boolean) {}

    override suspend fun recover(): L2capChannel = throw com.atruedev.kmpble.l2cap.L2capException.NotSupported()
}
