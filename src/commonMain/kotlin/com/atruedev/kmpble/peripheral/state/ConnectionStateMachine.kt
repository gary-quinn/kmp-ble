package com.atruedev.kmpble.peripheral.state

import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.OperationFailed
import kotlin.reflect.KClass

internal object ConnectionStateMachine {
    private val errorFallback = OperationFailed("Unknown error")

    private fun StateTransitionEvent.extractError(): BleError =
        when (this) {
            is StateTransitionEvent.BondFailed -> error ?: errorFallback
            is StateTransitionEvent.DiscoveryFailed -> error ?: errorFallback
            is StateTransitionEvent.ConfigurationFailed -> error ?: errorFallback
            is StateTransitionEvent.ConnectionLost -> error ?: errorFallback
            is StateTransitionEvent.RediscoveryFailed -> error ?: errorFallback
            else -> errorFallback
        }

    /**
     * Declarative transition table. Each entry maps (StateType, EventType) to resolver.
     * The resolver receives the concrete event to extract payload (e.g., error).
     */
    private val table: Map<Pair<KClass<*>, KClass<*>>, (ConnectionState, StateTransitionEvent) -> ConnectionState> =
        buildMap {
            // --- Disconnected.* -> Connecting ---
            on<ConnectionState.Disconnected, StateTransitionEvent.ConnectRequested> { _, _ -> ConnectionState.Connecting.Transport }

            // --- Connecting.Transport ---
            on<ConnectionState.Connecting.Transport, StateTransitionEvent.LinkEstablished> { _, _ -> ConnectionState.Connecting.Discovering }
            on<ConnectionState.Connecting.Transport, StateTransitionEvent.BondRequired> { _, _ -> ConnectionState.Connecting.Authenticating }
            on<ConnectionState.Connecting.Transport, StateTransitionEvent.ConnectionLost> { _, e -> ConnectionState.Disconnected.ByError(e.extractError()) }
            on<ConnectionState.Connecting.Transport, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnected.ByRequest }

            // --- Connecting.Authenticating ---
            on<ConnectionState.Connecting.Authenticating, StateTransitionEvent.BondSucceeded> { _, _ -> ConnectionState.Connecting.Discovering }
            on<ConnectionState.Connecting.Authenticating, StateTransitionEvent.BondFailed> { _, e -> ConnectionState.Disconnected.ByError(e.extractError()) }
            on<ConnectionState.Connecting.Authenticating, StateTransitionEvent.ConnectionLost> {
                _,
                e,
                ->
                ConnectionState.Disconnected.ByError(e.extractError())
            }
            on<ConnectionState.Connecting.Authenticating, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnected.ByRequest }

            // --- Connecting.Discovering ---
            on<ConnectionState.Connecting.Discovering, StateTransitionEvent.ServicesDiscovered> { _, _ -> ConnectionState.Connecting.Configuring }
            on<ConnectionState.Connecting.Discovering, StateTransitionEvent.DiscoveryFailed> {
                _,
                e,
                ->
                ConnectionState.Disconnected.ByError(e.extractError())
            }
            on<ConnectionState.Connecting.Discovering, StateTransitionEvent.ConnectionLost> {
                _,
                e,
                ->
                ConnectionState.Disconnected.ByError(e.extractError())
            }
            on<ConnectionState.Connecting.Discovering, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnected.ByRequest }

            // --- Connecting.Configuring ---
            on<ConnectionState.Connecting.Configuring, StateTransitionEvent.ConfigurationComplete> { _, _ -> ConnectionState.Connected.Ready }
            on<ConnectionState.Connecting.Configuring, StateTransitionEvent.ConfigurationFailed> {
                _,
                e,
                ->
                ConnectionState.Disconnected.ByError(e.extractError())
            }
            on<ConnectionState.Connecting.Configuring, StateTransitionEvent.InsufficientAuthentication> { _, _ -> ConnectionState.Connecting.Authenticating }
            on<ConnectionState.Connecting.Configuring, StateTransitionEvent.ConnectionLost> {
                _,
                e,
                ->
                ConnectionState.Disconnected.ByError(e.extractError())
            }
            on<ConnectionState.Connecting.Configuring, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnected.ByRequest }

            // --- Connected.Ready ---
            on<ConnectionState.Connected.Ready, StateTransitionEvent.BondStateChanged> { _, _ -> ConnectionState.Connected.BondingChange }
            on<ConnectionState.Connected.Ready, StateTransitionEvent.ServiceChangedIndication> { _, _ -> ConnectionState.Connected.ServiceChanged }
            on<ConnectionState.Connected.Ready, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnecting.Requested }
            on<ConnectionState.Connected.Ready, StateTransitionEvent.ConnectionLost> { _, _ -> ConnectionState.Disconnecting.Error }

            // --- Connected.BondingChange ---
            on<ConnectionState.Connected.BondingChange, StateTransitionEvent.BondChangeProcessed> { _, _ -> ConnectionState.Connected.Ready }
            on<ConnectionState.Connected.BondingChange, StateTransitionEvent.ServiceChangedIndication> { _, _ -> ConnectionState.Connected.ServiceChanged }
            on<ConnectionState.Connected.BondingChange, StateTransitionEvent.ConnectionLost> { _, _ -> ConnectionState.Disconnecting.Error }
            on<ConnectionState.Connected.BondingChange, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnecting.Requested }

            // --- Connected.ServiceChanged ---
            on<ConnectionState.Connected.ServiceChanged, StateTransitionEvent.RediscoverySucceeded> { _, _ -> ConnectionState.Connected.Ready }
            on<ConnectionState.Connected.ServiceChanged, StateTransitionEvent.RediscoveryFailed> { _, _ -> ConnectionState.Disconnecting.Error }
            on<ConnectionState.Connected.ServiceChanged, StateTransitionEvent.ConnectionLost> { _, _ -> ConnectionState.Disconnecting.Error }
            on<ConnectionState.Connected.ServiceChanged, StateTransitionEvent.DisconnectRequested> { _, _ -> ConnectionState.Disconnecting.Requested }

            // --- Disconnecting.Requested ---
            on<ConnectionState.Disconnecting.Requested, StateTransitionEvent.ConnectionLost> { _, _ -> ConnectionState.Disconnected.ByRequest }

            // --- Disconnecting.Error ---
            on<ConnectionState.Disconnecting.Error, StateTransitionEvent.SupervisionTimeout> { _, _ -> ConnectionState.Disconnected.ByTimeout }
            on<ConnectionState.Disconnecting.Error, StateTransitionEvent.ConnectionLost> { _, e -> ConnectionState.Disconnected.ByError(e.extractError()) }
        }

    /**
     * Wildcard transitions - apply from any non-Disconnected state.
     * Checked before the table. Order matters: AdapterOff and RemoteDisconnected
     * take precedence over state-specific transitions.
     */
    private val wildcards: List<Pair<KClass<out StateTransitionEvent>, (ConnectionState, StateTransitionEvent) -> ConnectionState?>> =
        listOf(
            StateTransitionEvent.AdapterOff::class to { s, _ ->
                if (s !is ConnectionState.Disconnected) ConnectionState.Disconnected.BySystemEvent else null
            },
            StateTransitionEvent.RemoteDisconnected::class to { s, _ ->
                if (s !is ConnectionState.Disconnected) ConnectionState.Disconnected.ByRemote else null
            },
        )

    data class TransitionResult(
        val newState: ConnectionState,
        val valid: Boolean,
    )

    fun transition(
        current: ConnectionState,
        event: StateTransitionEvent,
    ): TransitionResult {
        // Check wildcards first
        for ((eventClass, resolver) in wildcards) {
            if (eventClass.isInstance(event)) {
                val result = resolver(current, event)
                if (result != null) return TransitionResult(result, valid = true)
            }
        }

        // Look up in transition table - walk the state class hierarchy
        val resolver = findResolver(current, event)
        if (resolver != null) {
            return TransitionResult(resolver(current, event), valid = true)
        }

        // No valid transition found - return current state, flag as invalid
        return TransitionResult(current, valid = false)
    }

    private fun findResolver(
        state: ConnectionState,
        event: StateTransitionEvent,
    ): ((ConnectionState, StateTransitionEvent) -> ConnectionState)? {
        // Try exact match first, then walk up the state hierarchy
        val eventClass = event::class
        var stateClass: KClass<*>? = state::class

        while (stateClass != null) {
            val key = Pair(stateClass, eventClass)
            val resolver = table[key]
            if (resolver != null) return resolver

            // Walk up: e.g., Disconnected.ByRequest -> Disconnected -> ConnectionState
            stateClass = stateClass.superclasses.firstOrNull()
        }
        return null
    }

    private val stateParentMap: Map<KClass<*>, KClass<*>> =
        mapOf(
            ConnectionState.Disconnected.ByRequest::class to ConnectionState.Disconnected::class,
            ConnectionState.Disconnected.ByRemote::class to ConnectionState.Disconnected::class,
            ConnectionState.Disconnected.ByError::class to ConnectionState.Disconnected::class,
            ConnectionState.Disconnected.ByTimeout::class to ConnectionState.Disconnected::class,
            ConnectionState.Disconnected.BySystemEvent::class to ConnectionState.Disconnected::class,
        )

    /** All registered parent mappings, for test verification. */
    val allParentMappings: Map<KClass<*>, KClass<*>>
        get() = stateParentMap

    private val KClass<*>.superclasses: List<KClass<*>>
        get() = listOfNotNull(stateParentMap[this])

    /** All registered transitions, for test verification. */
    val allTransitions: Set<Pair<KClass<*>, KClass<*>>>
        get() = table.keys

    private inline fun <
        reified S : ConnectionState,
        reified E : StateTransitionEvent,
    > MutableMap<Pair<KClass<*>, KClass<*>>, (ConnectionState, StateTransitionEvent) -> ConnectionState>.on(
        noinline resolver: (S, E) -> ConnectionState,
    ) {
        put(Pair(S::class, E::class)) { state, event ->
            resolver(state as S, event as E)
        }
    }
}
