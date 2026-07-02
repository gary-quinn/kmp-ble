package com.atruedev.kmpble.peripheral.state

import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.OperationFailed
import kotlin.reflect.KClass

internal object StateMachine {
    private val errorFallback = OperationFailed("Unknown error")

    private fun ConnectionEvent.extractError(): BleError =
        when (this) {
            is ConnectionEvent.BondFailed -> error ?: errorFallback
            is ConnectionEvent.DiscoveryFailed -> error ?: errorFallback
            is ConnectionEvent.ConfigurationFailed -> error ?: errorFallback
            is ConnectionEvent.ConnectionLost -> error ?: errorFallback
            is ConnectionEvent.RediscoveryFailed -> error ?: errorFallback
            else -> errorFallback
        }

    /**
     * Declarative transition table. Each entry maps (StateType, EventType) to resolver.
     * The resolver receives the concrete event to extract payload (e.g., error).
     */
    private val table: Map<Pair<KClass<*>, KClass<*>>, (State, ConnectionEvent) -> State> =
        buildMap {
            // --- Disconnected.* -> Connecting ---
            on<State.Disconnected, ConnectionEvent.ConnectRequested> { _, _ -> State.Connecting.Transport }

            // --- Connecting.Transport ---
            on<State.Connecting.Transport, ConnectionEvent.LinkEstablished> { _, _ -> State.Connecting.Discovering }
            on<State.Connecting.Transport, ConnectionEvent.BondRequired> { _, _ -> State.Connecting.Authenticating }
            on<State.Connecting.Transport, ConnectionEvent.ConnectionLost> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Transport, ConnectionEvent.DisconnectRequested> { _, _ -> State.Disconnected.ByRequest }

            // --- Connecting.Authenticating ---
            on<State.Connecting.Authenticating, ConnectionEvent.BondSucceeded> { _, _ -> State.Connecting.Discovering }
            on<State.Connecting.Authenticating, ConnectionEvent.BondFailed> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Authenticating, ConnectionEvent.ConnectionLost> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Authenticating, ConnectionEvent.DisconnectRequested> {
                _,
                _,
                ->
                State.Disconnected.ByRequest
            }

            // --- Connecting.Discovering ---
            on<State.Connecting.Discovering, ConnectionEvent.ServicesDiscovered> {
                _,
                _,
                ->
                State.Connecting.Configuring
            }
            on<State.Connecting.Discovering, ConnectionEvent.DiscoveryFailed> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Discovering, ConnectionEvent.ConnectionLost> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Discovering, ConnectionEvent.DisconnectRequested> {
                _,
                _,
                ->
                State.Disconnected.ByRequest
            }

            // --- Connecting.Configuring ---
            on<State.Connecting.Configuring, ConnectionEvent.ConfigurationComplete> { _, _ -> State.Connected.Ready }
            on<State.Connecting.Configuring, ConnectionEvent.ConfigurationFailed> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Configuring, ConnectionEvent.InsufficientAuthentication> {
                _,
                _,
                ->
                State.Connecting.Authenticating
            }
            on<State.Connecting.Configuring, ConnectionEvent.ConnectionLost> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
            on<State.Connecting.Configuring, ConnectionEvent.DisconnectRequested> {
                _,
                _,
                ->
                State.Disconnected.ByRequest
            }

            // --- Connected.Ready ---
            on<State.Connected.Ready, ConnectionEvent.BondStateChanged> { _, _ -> State.Connected.BondingChange }
            on<State.Connected.Ready, ConnectionEvent.ServiceChangedIndication> {
                _,
                _,
                ->
                State.Connected.ServiceChanged
            }
            on<State.Connected.Ready, ConnectionEvent.DisconnectRequested> { _, _ -> State.Disconnecting.Requested }
            on<State.Connected.Ready, ConnectionEvent.ConnectionLost> { _, _ -> State.Disconnecting.Error }

            // --- Connected.BondingChange ---
            on<State.Connected.BondingChange, ConnectionEvent.BondChangeProcessed> { _, _ -> State.Connected.Ready }
            on<State.Connected.BondingChange, ConnectionEvent.ServiceChangedIndication> {
                _,
                _,
                ->
                State.Connected.ServiceChanged
            }
            on<State.Connected.BondingChange, ConnectionEvent.ConnectionLost> { _, _ -> State.Disconnecting.Error }
            on<State.Connected.BondingChange, ConnectionEvent.DisconnectRequested> {
                _,
                _,
                ->
                State.Disconnecting.Requested
            }

            // --- Connected.ServiceChanged ---
            on<State.Connected.ServiceChanged, ConnectionEvent.RediscoverySucceeded> { _, _ -> State.Connected.Ready }
            on<State.Connected.ServiceChanged, ConnectionEvent.RediscoveryFailed> { _, _ -> State.Disconnecting.Error }
            on<State.Connected.ServiceChanged, ConnectionEvent.ConnectionLost> { _, _ -> State.Disconnecting.Error }
            on<State.Connected.ServiceChanged, ConnectionEvent.DisconnectRequested> {
                _,
                _,
                ->
                State.Disconnecting.Requested
            }

            // --- Disconnecting.Requested ---
            on<State.Disconnecting.Requested, ConnectionEvent.ConnectionLost> { _, _ -> State.Disconnected.ByRequest }

            // --- Disconnecting.Error ---
            on<State.Disconnecting.Error, ConnectionEvent.SupervisionTimeout> { _, _ -> State.Disconnected.ByTimeout }
            on<State.Disconnecting.Error, ConnectionEvent.ConnectionLost> {
                _,
                e,
                ->
                State.Disconnected.ByError(e.extractError())
            }
        }

    /**
     * Wildcard transitions - apply from any non-Disconnected state.
     * Checked before the table. Order matters: AdapterOff and RemoteDisconnected
     * take precedence over state-specific transitions.
     */
    private val wildcards: List<Pair<KClass<out ConnectionEvent>, (State, ConnectionEvent) -> State?>> =
        listOf(
            ConnectionEvent.AdapterOff::class to { s, _ ->
                if (s !is State.Disconnected) State.Disconnected.BySystemEvent else null
            },
            ConnectionEvent.RemoteDisconnected::class to { s, _ ->
                if (s !is State.Disconnected) State.Disconnected.ByRemote else null
            },
        )

    data class TransitionResult(
        val newState: State,
        val valid: Boolean,
    )

    fun transition(
        current: State,
        event: ConnectionEvent,
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
        state: State,
        event: ConnectionEvent,
    ): ((State, ConnectionEvent) -> State)? {
        // Try exact match first, then walk up the state hierarchy
        val eventClass = event::class
        var stateClass: KClass<*>? = state::class

        while (stateClass != null) {
            val key = Pair(stateClass, eventClass)
            val resolver = table[key]
            if (resolver != null) return resolver

            // Walk up: e.g., Disconnected.ByRequest -> Disconnected -> State
            stateClass = stateClass.superclasses.firstOrNull()
        }
        return null
    }

    private val stateParentMap: Map<KClass<*>, KClass<*>> =
        mapOf(
            State.Disconnected.ByRequest::class to State.Disconnected::class,
            State.Disconnected.ByRemote::class to State.Disconnected::class,
            State.Disconnected.ByError::class to State.Disconnected::class,
            State.Disconnected.ByTimeout::class to State.Disconnected::class,
            State.Disconnected.BySystemEvent::class to State.Disconnected::class,
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
        reified S : State,
        reified E : ConnectionEvent,
    > MutableMap<Pair<KClass<*>, KClass<*>>, (State, ConnectionEvent) -> State>.on(
        noinline resolver: (S, E) -> State,
    ) {
        put(Pair(S::class, E::class)) { state, event ->
            resolver(state as S, event as E)
        }
    }
}
