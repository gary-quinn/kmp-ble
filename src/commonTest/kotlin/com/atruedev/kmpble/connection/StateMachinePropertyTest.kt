package com.atruedev.kmpble.connection

import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.peripheral.state.StateTransitionEvent
import com.atruedev.kmpble.peripheral.state.ConnectionStateMachine
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StateMachinePropertyTest {
    private val testError = OperationFailed("test")

    private val allEvents: List<StateTransitionEvent> =
        listOf(
            StateTransitionEvent.ConnectRequested,
            StateTransitionEvent.LinkEstablished,
            StateTransitionEvent.BondRequired,
            StateTransitionEvent.BondSucceeded,
            StateTransitionEvent.BondFailed(testError),
            StateTransitionEvent.ServicesDiscovered,
            StateTransitionEvent.DiscoveryFailed(testError),
            StateTransitionEvent.ConfigurationComplete,
            StateTransitionEvent.ConfigurationFailed(testError),
            StateTransitionEvent.InsufficientAuthentication,
            StateTransitionEvent.BondStateChanged,
            StateTransitionEvent.ServiceChangedIndication,
            StateTransitionEvent.DisconnectRequested,
            StateTransitionEvent.ConnectionLost(testError),
            StateTransitionEvent.RemoteDisconnected,
            StateTransitionEvent.AdapterOff,
            StateTransitionEvent.SupervisionTimeout,
            StateTransitionEvent.RediscoverySucceeded,
            StateTransitionEvent.RediscoveryFailed(testError),
            StateTransitionEvent.BondChangeProcessed,
        )

    @Test
    fun randomEventSequencesNeverCrash() {
        val random = Random(seed = 42)
        repeat(1000) {
            var state: ConnectionState = ConnectionState.Disconnected.ByRequest
            repeat(50) {
                val event = allEvents[random.nextInt(allEvents.size)]
                val result = ConnectionStateMachine.transition(state, event)
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
            var state: ConnectionState = ConnectionState.Disconnected.ByRequest

            // Random sequence to get to some state
            repeat(20) {
                val event = allEvents[random.nextInt(allEvents.size)]
                val result = ConnectionStateMachine.transition(state, event)
                state = result.newState
            }

            // Force to disconnected via AdapterOff (wildcard)
            if (state !is ConnectionState.Disconnected) {
                state = ConnectionStateMachine.transition(state, StateTransitionEvent.AdapterOff).newState
            }

            // Must always be able to reconnect from any Disconnected state
            val reconnect = ConnectionStateMachine.transition(state, StateTransitionEvent.ConnectRequested)
            assertTrue(reconnect.valid, "Could not reconnect from $state")
        }
    }

    @Test
    fun noStateIsTerminal() {
        val allStates: List<ConnectionState> =
            listOf(
                ConnectionState.Connecting.Transport,
                ConnectionState.Connecting.Authenticating,
                ConnectionState.Connecting.Discovering,
                ConnectionState.Connecting.Configuring,
                ConnectionState.Connected.Ready,
                ConnectionState.Connected.BondingChange,
                ConnectionState.Connected.ServiceChanged,
                ConnectionState.Disconnecting.Requested,
                ConnectionState.Disconnecting.Error,
                ConnectionState.Disconnected.ByRequest,
                ConnectionState.Disconnected.ByRemote,
                ConnectionState.Disconnected.ByError(testError),
                ConnectionState.Disconnected.ByTimeout,
                ConnectionState.Disconnected.BySystemEvent,
            )

        for (state in allStates) {
            val hasValidTransition =
                allEvents.any { event ->
                    ConnectionStateMachine.transition(state, event).valid
                }
            assertTrue(hasValidTransition, "State ${state::class.simpleName} has no valid transitions")
        }
    }
}
