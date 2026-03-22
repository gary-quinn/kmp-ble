package com.atruedev.kmpble.bonding

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.StateMachine
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BondStateMachineTest {
    private fun transition(
        from: State,
        event: ConnectionEvent,
    ): State {
        val result = StateMachine.transition(from, event)
        assertTrue(result.valid)
        return result.newState
    }

    @Test
    fun explicitBondingPath() {
        var s: State = State.Disconnected.ByRequest
        s = transition(s, ConnectionEvent.ConnectRequested)
        assertIs<State.Connecting.Transport>(s)

        s = transition(s, ConnectionEvent.BondRequired)
        assertIs<State.Connecting.Authenticating>(s)

        s = transition(s, ConnectionEvent.BondSucceeded)
        assertIs<State.Connecting.Discovering>(s)

        s = transition(s, ConnectionEvent.ServicesDiscovered)
        assertIs<State.Connecting.Configuring>(s)

        s = transition(s, ConnectionEvent.ConfigurationComplete)
        assertIs<State.Connected.Ready>(s)
    }

    @Test
    fun implicitBondingDuringConfiguring() {
        var s: State = State.Connecting.Configuring
        s = transition(s, ConnectionEvent.InsufficientAuthentication)
        assertIs<State.Connecting.Authenticating>(s)

        s = transition(s, ConnectionEvent.BondSucceeded)
        assertIs<State.Connecting.Discovering>(s)
    }

    @Test
    fun bondFailureDuringAuthentication() {
        var s: State = State.Connecting.Authenticating
        s = transition(s, ConnectionEvent.BondFailed(ConnectionFailed("Pairing rejected")))
        assertIs<State.Disconnected.ByError>(s)
    }

    @Test
    fun midConnectionBondStateChange() {
        var s: State = State.Connected.Ready
        s = transition(s, ConnectionEvent.BondStateChanged)
        assertIs<State.Connected.BondingChange>(s)

        s = transition(s, ConnectionEvent.BondChangeProcessed)
        assertIs<State.Connected.Ready>(s)
    }

    @Test
    fun serviceChangedPreemptsBondChange() {
        var s: State = State.Connected.BondingChange
        s = transition(s, ConnectionEvent.ServiceChangedIndication)
        assertIs<State.Connected.ServiceChanged>(s)
    }

    @Test
    fun connectionLostDuringBondChange() {
        val s =
            transition(
                State.Connected.BondingChange,
                ConnectionEvent.ConnectionLost(ConnectionLost("Lost")),
            )
        assertIs<State.Disconnecting.Error>(s)
    }
}
