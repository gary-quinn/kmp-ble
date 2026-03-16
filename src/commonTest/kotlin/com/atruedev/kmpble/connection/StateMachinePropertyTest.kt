package com.atruedev.kmpble.connection

import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.StateMachine
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StateMachinePropertyTest {

    private val testError = OperationFailed("test")

    private val allEvents: List<ConnectionEvent> = listOf(
        ConnectionEvent.ConnectRequested,
        ConnectionEvent.LinkEstablished,
        ConnectionEvent.BondRequired,
        ConnectionEvent.BondSucceeded,
        ConnectionEvent.BondFailed(testError),
        ConnectionEvent.ServicesDiscovered,
        ConnectionEvent.DiscoveryFailed(testError),
        ConnectionEvent.ConfigurationComplete,
        ConnectionEvent.ConfigurationFailed(testError),
        ConnectionEvent.InsufficientAuthentication,
        ConnectionEvent.BondStateChanged,
        ConnectionEvent.ServiceChangedIndication,
        ConnectionEvent.DisconnectRequested,
        ConnectionEvent.ConnectionLost(testError),
        ConnectionEvent.RemoteDisconnected,
        ConnectionEvent.AdapterOff,
        ConnectionEvent.SupervisionTimeout,
        ConnectionEvent.RediscoverySucceeded,
        ConnectionEvent.RediscoveryFailed(testError),
        ConnectionEvent.BondChangeProcessed,
    )

    @Test
    fun randomEventSequencesNeverCrash() {
        val random = Random(seed = 42)
        repeat(1000) {
            var state: State = State.Disconnected.ByRequest
            repeat(50) {
                val event = allEvents[random.nextInt(allEvents.size)]
                val result = StateMachine.transition(state, event)
                assertNotNull(result)
                // Valid or invalid, we always get a state back
                assertNotNull(result.newState)
                state = result.newState
            }
        }
    }

    @Test
    fun randomSequencesAlwaysAllowReconnect() {
        val random = Random(seed = 123)
        repeat(100) {
            var state: State = State.Disconnected.ByRequest

            // Random sequence to get to some state
            repeat(20) {
                val event = allEvents[random.nextInt(allEvents.size)]
                val result = StateMachine.transition(state, event)
                state = result.newState
            }

            // Force to disconnected via AdapterOff (wildcard)
            if (state !is State.Disconnected) {
                state = StateMachine.transition(state, ConnectionEvent.AdapterOff).newState
            }

            // Must always be able to reconnect from any Disconnected state
            val reconnect = StateMachine.transition(state, ConnectionEvent.ConnectRequested)
            assertTrue(reconnect.valid, "Could not reconnect from $state")
        }
    }

    @Test
    fun noStateIsTerminal() {
        val allStates: List<State> = listOf(
            State.Connecting.Transport,
            State.Connecting.Authenticating,
            State.Connecting.Discovering,
            State.Connecting.Configuring,
            State.Connected.Ready,
            State.Connected.BondingChange,
            State.Connected.ServiceChanged,
            State.Disconnecting.Requested,
            State.Disconnecting.Error,
            State.Disconnected.ByRequest,
            State.Disconnected.ByRemote,
            State.Disconnected.ByError(testError),
            State.Disconnected.ByTimeout,
            State.Disconnected.BySystemEvent,
        )

        for (state in allStates) {
            val hasValidTransition = allEvents.any { event ->
                StateMachine.transition(state, event).valid
            }
            assertTrue(hasValidTransition, "State ${state::class.simpleName} has no valid transitions")
        }
    }
}
