package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tests for [awaitConnectSettled]: the platform connect() releases its slot from discovery
 * callbacks whose events the state machine may have rejected, so connect() must still wait
 * for Ready or Disconnected before it returns.
 */
class ConnectSettleTest {
    private val context = PeripheralContext(Identifier("AA:BB:CC:DD:EE:FF"))
    private val slots = LifecycleSlots()

    @AfterTest
    fun tearDown() {
        context.close()
    }

    /** Mirrors the platform connect(): wait for the connect slot, then for the state to settle. */
    private suspend fun startConnect(
        deadline: TimeMark,
        abort: suspend () -> Unit,
    ): Deferred<Unit> {
        val released = withContext(context.dispatcher) { slots.armConnect() }
        return context.scope.async {
            released.await()
            context.awaitConnectSettled(deadline, abort)
        }
    }

    private fun deadlineIn(timeout: Duration): TimeMark = TimeSource.Monotonic.markNow() + timeout

    /** The discovery callback: it releases the slot whether or not its events were accepted. */
    private suspend fun finishDiscovery() {
        context.processEvent(ConnectionEvent.ServicesDiscovered)
        context.processEvent(ConnectionEvent.ConfigurationComplete)
        withContext(context.dispatcher) { slots.completeConnect() }
    }

    /** Lets the connect coroutine, resumed on the serial dispatcher, run to its next suspension. */
    private suspend fun drainDispatcher() {
        withContext(context.dispatcher) {}
    }

    @Test
    fun connectWaitsForReadyWhenTheSlotIsReleasedMidHandshake() =
        runTest {
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.BondRequired)
            val connect = startConnect(deadlineIn(10.seconds)) { fail("a connect that settles must not abort") }

            finishDiscovery()
            drainDispatcher()

            assertIs<State.Connecting.Authenticating>(context.state.value)
            assertFalse(connect.isCompleted, "connect() must not return while the state is Connecting.*")

            context.processEvent(ConnectionEvent.BondSucceeded)
            drainDispatcher()
            assertFalse(connect.isCompleted, "connect() must not return while the state is Connecting.*")

            finishDiscovery()
            connect.await()

            assertIs<State.Connected.Ready>(context.state.value)
        }

    @Test
    fun connectThatNeverSettlesDisconnectsAndFailsWithConnectionFailed() =
        runTest {
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.BondRequired)
            var aborts = 0
            val connect = startConnect(deadlineIn(200.milliseconds)) { aborts++ }

            finishDiscovery()
            val error = assertIs<ConnectionFailed>(assertFailsWith<BleException> { connect.await() }.error)

            assertEquals(ConnectionFailureReason.TIMEOUT, error.failureReason)
            assertEquals(1, aborts)
            assertEquals(State.Disconnected.ByError(error), context.state.value)
        }

    @Test
    fun connectThatEndsDisconnectedReturnsWithoutAborting() =
        runTest {
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.LinkEstablished)
            val connect =
                startConnect(deadlineIn(10.seconds)) { fail("a connect that ended Disconnected must not abort") }

            context.processEvent(ConnectionEvent.DiscoveryFailed(OperationFailed("discovery failed")))
            withContext(context.dispatcher) { slots.completeConnect() }
            connect.await()

            assertIs<State.Disconnected.ByError>(context.state.value)
        }

    @Test
    fun settleWaitUsesWhatIsLeftOfTheConnectBudget() =
        // Well under the 30s budget: waiting a fresh window instead would time the test out.
        runTest(timeout = 10.seconds) {
            val clock = TestTimeSource()
            val deadline = clock.markNow() + 30.seconds
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.BondRequired)
            val connect = startConnect(deadline) {}

            // The transport phase used the whole budget; a fresh window would wait another 30s.
            clock += 30.seconds
            finishDiscovery()
            val error = assertIs<ConnectionFailed>(assertFailsWith<BleException> { connect.await() }.error)

            assertEquals(ConnectionFailureReason.TIMEOUT, error.failureReason)
        }

    @Test
    fun connectThatIsReadyAtTheDeadlineStillReturns() =
        runTest {
            val clock = TestTimeSource()
            val deadline = clock.markNow() + 30.seconds
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.LinkEstablished)
            val connect = startConnect(deadline) { fail("a connect that reached Ready must not abort") }

            clock += 30.seconds
            finishDiscovery()
            connect.await()

            assertIs<State.Connected.Ready>(context.state.value)
        }
}
