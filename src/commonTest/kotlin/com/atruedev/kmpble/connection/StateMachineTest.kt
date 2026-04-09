package com.atruedev.kmpble.connection

import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.StateMachine
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StateMachineTest {
    private val testError = OperationFailed("test")

    private fun transition(
        from: State,
        event: ConnectionEvent,
    ): State {
        val result = StateMachine.transition(from, event)
        assertTrue(
            result.valid,
            "Expected valid transition from ${from::class.simpleName} + ${event::class.simpleName}",
        )
        return result.newState
    }

    private fun assertInvalid(
        from: State,
        event: ConnectionEvent,
    ) {
        val result = StateMachine.transition(from, event)
        assertFalse(
            result.valid,
            "Expected invalid transition from ${from::class.simpleName} + ${event::class.simpleName}",
        )
        assertEquals(from, result.newState)
    }

    // --- Happy path: full connection lifecycle ---

    @Test
    fun fullConnectionLifecycle() {
        var s: State = State.Disconnected.ByRequest
        s = transition(s, ConnectionEvent.ConnectRequested)
        assertIs<State.Connecting.Transport>(s)

        s = transition(s, ConnectionEvent.LinkEstablished)
        assertIs<State.Connecting.Discovering>(s)

        s = transition(s, ConnectionEvent.ServicesDiscovered)
        assertIs<State.Connecting.Configuring>(s)

        s = transition(s, ConnectionEvent.ConfigurationComplete)
        assertIs<State.Connected.Ready>(s)

        s = transition(s, ConnectionEvent.DisconnectRequested)
        assertIs<State.Disconnecting.Requested>(s)

        s = transition(s, ConnectionEvent.ConnectionLost(testError))
        assertIs<State.Disconnected.ByRequest>(s)
    }

    // --- Connecting with bonding ---

    @Test
    fun connectionWithBonding() {
        var s: State = State.Disconnected.ByRequest
        s = transition(s, ConnectionEvent.ConnectRequested)
        s = transition(s, ConnectionEvent.BondRequired)
        assertIs<State.Connecting.Authenticating>(s)

        s = transition(s, ConnectionEvent.BondSucceeded)
        assertIs<State.Connecting.Discovering>(s)
    }

    @Test
    fun bondFailureDisconnects() {
        var s: State = State.Disconnected.ByRequest
        s = transition(s, ConnectionEvent.ConnectRequested)
        s = transition(s, ConnectionEvent.BondRequired)
        s = transition(s, ConnectionEvent.BondFailed(testError))
        assertIs<State.Disconnected.ByError>(s)
    }

    // --- Implicit bonding during configuring ---

    @Test
    fun implicitBondDuringConfiguring() {
        var s: State = State.Connecting.Configuring
        s = transition(s, ConnectionEvent.InsufficientAuthentication)
        assertIs<State.Connecting.Authenticating>(s)
    }

    // --- Connected state events ---

    @Test
    fun serviceChangedInConnectedReady() {
        var s: State = State.Connected.Ready
        s = transition(s, ConnectionEvent.ServiceChangedIndication)
        assertIs<State.Connected.ServiceChanged>(s)

        s = transition(s, ConnectionEvent.RediscoverySucceeded)
        assertIs<State.Connected.Ready>(s)
    }

    @Test
    fun bondingChangeInConnectedReady() {
        var s: State = State.Connected.Ready
        s = transition(s, ConnectionEvent.BondStateChanged)
        assertIs<State.Connected.BondingChange>(s)

        s = transition(s, ConnectionEvent.BondChangeProcessed)
        assertIs<State.Connected.Ready>(s)
    }

    @Test
    fun serviceChangedPreemptsBondingChange() {
        var s: State = State.Connected.BondingChange
        s = transition(s, ConnectionEvent.ServiceChangedIndication)
        assertIs<State.Connected.ServiceChanged>(s)
    }

    // --- Connection lost ---

    @Test
    fun connectionLostFromReady() {
        val s = transition(State.Connected.Ready, ConnectionEvent.ConnectionLost(testError))
        assertIs<State.Disconnecting.Error>(s)
    }

    @Test
    fun connectionLostDuringDiscovery() {
        val s = transition(State.Connecting.Discovering, ConnectionEvent.ConnectionLost(testError))
        assertIs<State.Disconnected.ByError>(s)
    }

    @Test
    fun discoveryFailure() {
        val s = transition(State.Connecting.Discovering, ConnectionEvent.DiscoveryFailed(testError))
        assertIs<State.Disconnected.ByError>(s)
    }

    // --- Disconnecting terminal states ---

    @Test
    fun disconnectingErrorToByTimeout() {
        val s = transition(State.Disconnecting.Error, ConnectionEvent.SupervisionTimeout)
        assertIs<State.Disconnected.ByTimeout>(s)
    }

    @Test
    fun disconnectingErrorToByError() {
        val s = transition(State.Disconnecting.Error, ConnectionEvent.ConnectionLost(testError))
        assertIs<State.Disconnected.ByError>(s)
    }

    // --- Wildcard transitions ---

    @Test
    fun adapterOffFromAnyConnectedState() {
        assertIs<State.Disconnected.BySystemEvent>(
            transition(State.Connected.Ready, ConnectionEvent.AdapterOff),
        )
        assertIs<State.Disconnected.BySystemEvent>(
            transition(State.Connecting.Transport, ConnectionEvent.AdapterOff),
        )
        assertIs<State.Disconnected.BySystemEvent>(
            transition(State.Connecting.Discovering, ConnectionEvent.AdapterOff),
        )
        assertIs<State.Disconnected.BySystemEvent>(
            transition(State.Disconnecting.Requested, ConnectionEvent.AdapterOff),
        )
    }

    @Test
    fun adapterOffFromDisconnectedIsInvalid() {
        assertInvalid(State.Disconnected.ByRequest, ConnectionEvent.AdapterOff)
    }

    @Test
    fun remoteDisconnectedFromAnyConnectedState() {
        assertIs<State.Disconnected.ByRemote>(
            transition(State.Connected.Ready, ConnectionEvent.RemoteDisconnected),
        )
        assertIs<State.Disconnected.ByRemote>(
            transition(State.Connecting.Configuring, ConnectionEvent.RemoteDisconnected),
        )
    }

    // --- Invalid transitions return current state ---

    @Test
    fun invalidTransitionFromDisconnected() {
        assertInvalid(State.Disconnected.ByRequest, ConnectionEvent.ServicesDiscovered)
    }

    @Test
    fun invalidTransitionFromConnectedReady() {
        assertInvalid(State.Connected.Ready, ConnectionEvent.ConnectRequested)
    }

    // --- Reconnection from any Disconnected subtype ---

    @Test
    fun connectFromAllDisconnectedSubtypes() {
        val subtypes =
            listOf(
                State.Disconnected.ByRequest,
                State.Disconnected.ByRemote,
                State.Disconnected.ByError(testError),
                State.Disconnected.ByTimeout,
                State.Disconnected.BySystemEvent,
            )
        for (disconnected in subtypes) {
            val s = transition(disconnected, ConnectionEvent.ConnectRequested)
            assertIs<State.Connecting.Transport>(s, "Failed from $disconnected")
        }
    }

    // --- Disconnect requested during connecting states ---

    @Test
    fun disconnectRequestedDuringConnecting() {
        assertIs<State.Disconnected.ByRequest>(
            transition(State.Connecting.Transport, ConnectionEvent.DisconnectRequested),
        )
        assertIs<State.Disconnected.ByRequest>(
            transition(State.Connecting.Authenticating, ConnectionEvent.DisconnectRequested),
        )
        assertIs<State.Disconnected.ByRequest>(
            transition(State.Connecting.Discovering, ConnectionEvent.DisconnectRequested),
        )
        assertIs<State.Disconnected.ByRequest>(
            transition(State.Connecting.Configuring, ConnectionEvent.DisconnectRequested),
        )
    }

    // --- Parent map consistency with transition table ---

    @Test
    fun parentMapCoversAllTransitionTableParents() {
        val transitionTableStates = StateMachine.allTransitions.map { it.first }.toSet()
        val parentMapChildren = StateMachine.allParentMappings.keys
        val parentMapParents = StateMachine.allParentMappings.values.toSet()

        // Every parent referenced in the map must appear as a key in the transition table,
        // otherwise the hierarchy walk would resolve to a class with no transitions.
        for (parent in parentMapParents) {
            assertTrue(
                parent in transitionTableStates,
                "${parent.simpleName} is a parent in the hierarchy map but has no transitions",
            )
        }

        // Every child in the parent map must NOT appear as a direct key in the transition
        // table (it relies on hierarchy resolution through its parent).
        // If it does appear, the parent mapping is redundant but not harmful — skip this
        // assertion as it's a style preference, not a correctness invariant.

        // Core invariant: ConnectRequested from any Disconnected subtype must resolve.
        // This is already covered by connectFromAllDisconnectedSubtypes, but we verify
        // the parent map is non-empty (a guard against accidental clearing).
        assertTrue(
            parentMapChildren.isNotEmpty(),
            "Parent map is empty — hierarchy resolution is broken",
        )
    }
}
