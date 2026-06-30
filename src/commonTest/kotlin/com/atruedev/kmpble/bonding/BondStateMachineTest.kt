package com.atruedev.kmpble.bonding

import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.peripheral.state.StateTransitionEvent
import com.atruedev.kmpble.peripheral.state.ConnectionStateMachine
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BondStateMachineTest {
    private fun transition(
        from: ConnectionState,
        event: StateTransitionEvent,
    ): ConnectionState {
        val result = ConnectionStateMachine.transition(from, event)
        assertTrue(result.valid)
        return result.newState
    }

    @Test
    fun explicitBondingPath() {
        var s: ConnectionState = ConnectionState.Disconnected.ByRequest
        s = transition(s, StateTransitionEvent.ConnectRequested)
        assertIs<ConnectionState.Connecting.Transport>(s)

        s = transition(s, StateTransitionEvent.BondRequired)
        assertIs<ConnectionState.Connecting.Authenticating>(s)

        s = transition(s, StateTransitionEvent.BondSucceeded)
        assertIs<ConnectionState.Connecting.Discovering>(s)

        s = transition(s, StateTransitionEvent.ServicesDiscovered)
        assertIs<ConnectionState.Connecting.Configuring>(s)

        s = transition(s, StateTransitionEvent.ConfigurationComplete)
        assertIs<ConnectionState.Connected.Ready>(s)
    }

    @Test
    fun implicitBondingDuringConfiguring() {
        var s: ConnectionState = ConnectionState.Connecting.Configuring
        s = transition(s, StateTransitionEvent.InsufficientAuthentication)
        assertIs<ConnectionState.Connecting.Authenticating>(s)

        s = transition(s, StateTransitionEvent.BondSucceeded)
        assertIs<ConnectionState.Connecting.Discovering>(s)
    }

    @Test
    fun bondFailureDuringAuthentication() {
        var s: ConnectionState = ConnectionState.Connecting.Authenticating
        s = transition(s, StateTransitionEvent.BondFailed(ConnectionFailed("Pairing rejected")))
        assertIs<ConnectionState.Disconnected.ByError>(s)
    }

    @Test
    fun midConnectionBondStateChange() {
        var s: ConnectionState = ConnectionState.Connected.Ready
        s = transition(s, StateTransitionEvent.BondStateChanged)
        assertIs<ConnectionState.Connected.BondingChange>(s)

        s = transition(s, StateTransitionEvent.BondChangeProcessed)
        assertIs<ConnectionState.Connected.Ready>(s)
    }

    @Test
    fun serviceChangedPreemptsBondChange() {
        var s: ConnectionState = ConnectionState.Connected.BondingChange
        s = transition(s, StateTransitionEvent.ServiceChangedIndication)
        assertIs<ConnectionState.Connected.ServiceChanged>(s)
    }

    @Test
    fun connectionLostDuringBondChange() {
        val s =
            transition(
                ConnectionState.Connected.BondingChange,
                StateTransitionEvent.ConnectionLost(ConnectionLost("Lost")),
            )
        assertIs<ConnectionState.Disconnecting.Error>(s)
    }
}
