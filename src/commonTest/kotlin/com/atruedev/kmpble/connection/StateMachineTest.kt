package com.atruedev.kmpble.connection

import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.peripheral.state.StateTransitionEvent
import com.atruedev.kmpble.peripheral.state.ConnectionStateMachine
import com.atruedev.kmpble.error.OperationFailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StateMachineTest {
    private val testError = OperationFailed("test")

    private fun transition(
        from: ConnectionState,
        event: StateTransitionEvent,
    ): ConnectionState {
        val result = ConnectionStateMachine.transition(from, event)
        assertTrue(
            result.valid,
            "Expected valid transition from ${from::class.simpleName} + ${event::class.simpleName}",
        )
        return result.newState
    }

    private fun assertInvalid(
        from: ConnectionState,
        event: StateTransitionEvent,
    ) {
        val result = ConnectionStateMachine.transition(from, event)
        assertFalse(
            result.valid,
            "Expected invalid transition from ${from::class.simpleName} + ${event::class.simpleName}",
        )
        assertEquals(from, result.newState)
    }

    // --- Happy path: full connection lifecycle ---

    @Test
    fun fullConnectionLifecycle() {
        var s: ConnectionState = ConnectionState.Disconnected.ByRequest
        s = transition(s, StateTransitionEvent.ConnectRequested)
        assertIs<ConnectionState.Connecting.Transport>(s)

        s = transition(s, StateTransitionEvent.LinkEstablished)
        assertIs<ConnectionState.Connecting.Discovering>(s)

        s = transition(s, StateTransitionEvent.ServicesDiscovered)
        assertIs<ConnectionState.Connecting.Configuring>(s)

        s = transition(s, StateTransitionEvent.ConfigurationComplete)
        assertIs<ConnectionState.Connected.Ready>(s)

        s = transition(s, StateTransitionEvent.DisconnectRequested)
        assertIs<ConnectionState.Disconnecting.Requested>(s)

        s = transition(s, StateTransitionEvent.ConnectionLost(testError))
        assertIs<ConnectionState.Disconnected.ByRequest>(s)
    }

    // --- Connecting with bonding ---

    @Test
    fun connectionWithBonding() {
        var s: ConnectionState = ConnectionState.Disconnected.ByRequest
        s = transition(s, StateTransitionEvent.ConnectRequested)
        s = transition(s, StateTransitionEvent.BondRequired)
        assertIs<ConnectionState.Connecting.Authenticating>(s)

        s = transition(s, StateTransitionEvent.BondSucceeded)
        assertIs<ConnectionState.Connecting.Discovering>(s)
    }

    @Test
    fun bondFailureDisconnects() {
        var s: ConnectionState = ConnectionState.Disconnected.ByRequest
        s = transition(s, StateTransitionEvent.ConnectRequested)
        s = transition(s, StateTransitionEvent.BondRequired)
        s = transition(s, StateTransitionEvent.BondFailed(testError))
        assertIs<ConnectionState.Disconnected.ByError>(s)
    }

    // --- Implicit bonding during configuring ---

    @Test
    fun implicitBondDuringConfiguring() {
        var s: ConnectionState = ConnectionState.Connecting.Configuring
        s = transition(s, StateTransitionEvent.InsufficientAuthentication)
        assertIs<ConnectionState.Connecting.Authenticating>(s)
    }

    // --- Connected state events ---

    @Test
    fun serviceChangedInConnectedReady() {
        var s: ConnectionState = ConnectionState.Connected.Ready
        s = transition(s, StateTransitionEvent.ServiceChangedIndication)
        assertIs<ConnectionState.Connected.ServiceChanged>(s)

        s = transition(s, StateTransitionEvent.RediscoverySucceeded)
        assertIs<ConnectionState.Connected.Ready>(s)
    }

    @Test
    fun bondingChangeInConnectedReady() {
        var s: ConnectionState = ConnectionState.Connected.Ready
        s = transition(s, StateTransitionEvent.BondStateChanged)
        assertIs<ConnectionState.Connected.BondingChange>(s)

        s = transition(s, StateTransitionEvent.BondChangeProcessed)
        assertIs<ConnectionState.Connected.Ready>(s)
    }

    @Test
    fun serviceChangedPreemptsBondingChange() {
        var s: ConnectionState = ConnectionState.Connected.BondingChange
        s = transition(s, StateTransitionEvent.ServiceChangedIndication)
        assertIs<ConnectionState.Connected.ServiceChanged>(s)
    }

    // --- Connection lost ---

    @Test
    fun connectionLostFromReady() {
        val s = transition(ConnectionState.Connected.Ready, StateTransitionEvent.ConnectionLost(testError))
        assertIs<ConnectionState.Disconnecting.Error>(s)
    }

    @Test
    fun connectionLostDuringDiscovery() {
        val s = transition(ConnectionState.Connecting.Discovering, StateTransitionEvent.ConnectionLost(testError))
        assertIs<ConnectionState.Disconnected.ByError>(s)
    }

    @Test
    fun discoveryFailure() {
        val s = transition(ConnectionState.Connecting.Discovering, StateTransitionEvent.DiscoveryFailed(testError))
        assertIs<ConnectionState.Disconnected.ByError>(s)
    }

    // --- Disconnecting terminal states ---

    @Test
    fun disconnectingErrorToByTimeout() {
        val s = transition(ConnectionState.Disconnecting.Error, StateTransitionEvent.SupervisionTimeout)
        assertIs<ConnectionState.Disconnected.ByTimeout>(s)
    }

    @Test
    fun disconnectingErrorToByError() {
        val s = transition(ConnectionState.Disconnecting.Error, StateTransitionEvent.ConnectionLost(testError))
        assertIs<ConnectionState.Disconnected.ByError>(s)
    }

    // --- Wildcard transitions ---

    @Test
    fun adapterOffFromAnyConnectedState() {
        assertIs<ConnectionState.Disconnected.BySystemEvent>(
            transition(ConnectionState.Connected.Ready, StateTransitionEvent.AdapterOff),
        )
        assertIs<ConnectionState.Disconnected.BySystemEvent>(
            transition(ConnectionState.Connecting.Transport, StateTransitionEvent.AdapterOff),
        )
        assertIs<ConnectionState.Disconnected.BySystemEvent>(
            transition(ConnectionState.Connecting.Discovering, StateTransitionEvent.AdapterOff),
        )
        assertIs<ConnectionState.Disconnected.BySystemEvent>(
            transition(ConnectionState.Disconnecting.Requested, StateTransitionEvent.AdapterOff),
        )
    }

    @Test
    fun adapterOffFromDisconnectedIsInvalid() {
        assertInvalid(ConnectionState.Disconnected.ByRequest, StateTransitionEvent.AdapterOff)
    }

    @Test
    fun remoteDisconnectedFromAnyConnectedState() {
        assertIs<ConnectionState.Disconnected.ByRemote>(
            transition(ConnectionState.Connected.Ready, StateTransitionEvent.RemoteDisconnected),
        )
        assertIs<ConnectionState.Disconnected.ByRemote>(
            transition(ConnectionState.Connecting.Configuring, StateTransitionEvent.RemoteDisconnected),
        )
    }

    // --- Invalid transitions return current state ---

    @Test
    fun invalidTransitionFromDisconnected() {
        assertInvalid(ConnectionState.Disconnected.ByRequest, StateTransitionEvent.ServicesDiscovered)
    }

    @Test
    fun invalidTransitionFromConnectedReady() {
        assertInvalid(ConnectionState.Connected.Ready, StateTransitionEvent.ConnectRequested)
    }

    // --- Reconnection from any Disconnected subtype ---

    @Test
    fun connectFromAllDisconnectedSubtypes() {
        val subtypes =
            listOf(
                ConnectionState.Disconnected.ByRequest,
                ConnectionState.Disconnected.ByRemote,
                ConnectionState.Disconnected.ByError(testError),
                ConnectionState.Disconnected.ByTimeout,
                ConnectionState.Disconnected.BySystemEvent,
            )
        for (disconnected in subtypes) {
            val s = transition(disconnected, StateTransitionEvent.ConnectRequested)
            assertIs<ConnectionState.Connecting.Transport>(s, "Failed from $disconnected")
        }
    }

    // --- Disconnect requested during connecting states ---

    @Test
    fun disconnectRequestedDuringConnecting() {
        assertIs<ConnectionState.Disconnected.ByRequest>(
            transition(ConnectionState.Connecting.Transport, StateTransitionEvent.DisconnectRequested),
        )
        assertIs<ConnectionState.Disconnected.ByRequest>(
            transition(ConnectionState.Connecting.Authenticating, StateTransitionEvent.DisconnectRequested),
        )
        assertIs<ConnectionState.Disconnected.ByRequest>(
            transition(ConnectionState.Connecting.Discovering, StateTransitionEvent.DisconnectRequested),
        )
        assertIs<ConnectionState.Disconnected.ByRequest>(
            transition(ConnectionState.Connecting.Configuring, StateTransitionEvent.DisconnectRequested),
        )
    }
}
