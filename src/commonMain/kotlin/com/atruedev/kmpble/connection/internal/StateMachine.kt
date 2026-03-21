package com.atruedev.kmpble.connection.internal

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.State.*
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.OperationFailed
import kotlin.reflect.KClass

internal object StateMachine {

    private val errorFallback = OperationFailed("Unknown error")

    private fun ConnectionEvent.extractError(): BleError = when (this) {
        is ConnectionEvent.BondFailed -> error ?: errorFallback
        is ConnectionEvent.DiscoveryFailed -> error ?: errorFallback
        is ConnectionEvent.ConfigurationFailed -> error ?: errorFallback
        is ConnectionEvent.ConnectionLost -> error ?: errorFallback
        is ConnectionEvent.RediscoveryFailed -> error ?: errorFallback
        else -> errorFallback
    }

    /**
     * Declarative transition table. Each entry maps (StateType, EventType) → resolver.
     * The resolver receives the concrete event to extract payload (e.g., error).
     */
    private val table: Map<Pair<KClass<*>, KClass<*>>, (State, ConnectionEvent) -> State> =
        buildMap {
            // --- Disconnected.* → Connecting ---
            on<Disconnected, ConnectionEvent.ConnectRequested> { _, _ -> Connecting.Transport }

            // --- Connecting.Transport ---
            on<Connecting.Transport, ConnectionEvent.LinkEstablished> { _, _ -> Connecting.Discovering }
            on<Connecting.Transport, ConnectionEvent.BondRequired> { _, _ -> Connecting.Authenticating }
            on<Connecting.Transport, ConnectionEvent.ConnectionLost> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Transport, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnected.ByRequest }

            // --- Connecting.Authenticating ---
            on<Connecting.Authenticating, ConnectionEvent.BondSucceeded> { _, _ -> Connecting.Discovering }
            on<Connecting.Authenticating, ConnectionEvent.BondFailed> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Authenticating, ConnectionEvent.ConnectionLost> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Authenticating, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnected.ByRequest }

            // --- Connecting.Discovering ---
            on<Connecting.Discovering, ConnectionEvent.ServicesDiscovered> { _, _ -> Connecting.Configuring }
            on<Connecting.Discovering, ConnectionEvent.DiscoveryFailed> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Discovering, ConnectionEvent.ConnectionLost> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Discovering, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnected.ByRequest }

            // --- Connecting.Configuring ---
            on<Connecting.Configuring, ConnectionEvent.ConfigurationComplete> { _, _ -> Connected.Ready }
            on<Connecting.Configuring, ConnectionEvent.ConfigurationFailed> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Configuring, ConnectionEvent.InsufficientAuthentication> { _, _ -> Connecting.Authenticating }
            on<Connecting.Configuring, ConnectionEvent.ConnectionLost> { _, e -> Disconnected.ByError(e.extractError()) }
            on<Connecting.Configuring, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnected.ByRequest }

            // --- Connected.Ready ---
            on<Connected.Ready, ConnectionEvent.BondStateChanged> { _, _ -> Connected.BondingChange }
            on<Connected.Ready, ConnectionEvent.ServiceChangedIndication> { _, _ -> Connected.ServiceChanged }
            on<Connected.Ready, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnecting.Requested }
            on<Connected.Ready, ConnectionEvent.ConnectionLost> { _, _ -> Disconnecting.Error }

            // --- Connected.BondingChange ---
            on<Connected.BondingChange, ConnectionEvent.BondChangeProcessed> { _, _ -> Connected.Ready }
            on<Connected.BondingChange, ConnectionEvent.ServiceChangedIndication> { _, _ -> Connected.ServiceChanged }
            on<Connected.BondingChange, ConnectionEvent.ConnectionLost> { _, _ -> Disconnecting.Error }
            on<Connected.BondingChange, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnecting.Requested }

            // --- Connected.ServiceChanged ---
            on<Connected.ServiceChanged, ConnectionEvent.RediscoverySucceeded> { _, _ -> Connected.Ready }
            on<Connected.ServiceChanged, ConnectionEvent.RediscoveryFailed> { _, _ -> Disconnecting.Error }
            on<Connected.ServiceChanged, ConnectionEvent.ConnectionLost> { _, _ -> Disconnecting.Error }
            on<Connected.ServiceChanged, ConnectionEvent.DisconnectRequested> { _, _ -> Disconnecting.Requested }

            // --- Disconnecting.Requested ---
            on<Disconnecting.Requested, ConnectionEvent.ConnectionLost> { _, _ -> Disconnected.ByRequest }

            // --- Disconnecting.Error ---
            on<Disconnecting.Error, ConnectionEvent.SupervisionTimeout> { _, _ -> Disconnected.ByTimeout }
            on<Disconnecting.Error, ConnectionEvent.ConnectionLost> { _, e -> Disconnected.ByError(e.extractError()) }
        }

    /**
     * Wildcard transitions — apply from any non-Disconnected state.
     * Checked before the table. Order matters: AdapterOff and RemoteDisconnected
     * take precedence over state-specific transitions.
     */
    private val wildcards: List<Pair<KClass<out ConnectionEvent>, (State, ConnectionEvent) -> State?>> = listOf(
        ConnectionEvent.AdapterOff::class to { s, _ ->
            if (s !is Disconnected) Disconnected.BySystemEvent else null
        },
        ConnectionEvent.RemoteDisconnected::class to { s, _ ->
            if (s !is Disconnected) Disconnected.ByRemote else null
        },
    )

    data class TransitionResult(
        val newState: State,
        val valid: Boolean,
    )

    fun transition(current: State, event: ConnectionEvent): TransitionResult {
        // Check wildcards first
        for ((eventClass, resolver) in wildcards) {
            if (eventClass.isInstance(event)) {
                val result = resolver(current, event)
                if (result != null) return TransitionResult(result, valid = true)
            }
        }

        // Look up in transition table — walk the state class hierarchy
        val resolver = findResolver(current, event)
        if (resolver != null) {
            return TransitionResult(resolver(current, event), valid = true)
        }

        // No valid transition found — return current state, flag as invalid
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

            // Walk up: e.g., Disconnected.ByRequest → Disconnected → State
            stateClass = stateClass.superclasses.firstOrNull()
        }
        return null
    }

    private val KClass<*>.superclasses: List<KClass<*>>
        get() = when (this) {
            Disconnected.ByRequest::class,
            Disconnected.ByRemote::class,
            Disconnected.ByError::class,
            Disconnected.ByTimeout::class,
            Disconnected.BySystemEvent::class -> listOf(Disconnected::class)
            else -> emptyList()
        }

    /** All registered transitions, for test verification. */
    val allTransitions: Set<Pair<KClass<*>, KClass<*>>>
        get() = table.keys

    private inline fun <reified S : State, reified E : ConnectionEvent>
        MutableMap<Pair<KClass<*>, KClass<*>>, (State, ConnectionEvent) -> State>.on(
        noinline resolver: (S, E) -> State,
    ) {
        put(Pair(S::class, E::class)) { state, event -> resolver(state as S, event as E) }
    }
}
