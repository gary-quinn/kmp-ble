package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.StateMachine
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.internal.GattOperationQueue
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.TimeSource

internal class PeripheralContext(val identifier: Identifier) {

    val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineName("Peripheral/${identifier.value}")
    )

    private val _state = MutableStateFlow(State.Disconnected.ByRequest as State)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _services = MutableStateFlow<List<DiscoveredService>?>(null)
    val services: StateFlow<List<DiscoveredService>?> = _services.asStateFlow()

    private val _bondState = MutableStateFlow<BondState>(BondState.Unknown)
    val bondState: StateFlow<BondState> = _bondState.asStateFlow()

    private val _maximumWriteValueLength = MutableStateFlow(DEFAULT_ATT_MTU - ATT_HEADER_SIZE)
    val maximumWriteValueLength: StateFlow<Int> = _maximumWriteValueLength.asStateFlow()

    val gattQueue = GattOperationQueue(scope)

    @Volatile
    private var closed = false

    /**
     * Tracks when the current state was entered, for connection timeline logging.
     * Confined to [dispatcher] — only read/written inside [processEvent].
     */
    private var stateEnteredAt: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * Process a state machine event. Always runs on the peripheral's serialized dispatcher.
     * Returns the new state. Invalid transitions are logged and ignored (no crash).
     *
     * Logs [BleLogEvent.StateTransition] with the duration spent in the previous state,
     * enabling connection timeline analysis.
     */
    suspend fun processEvent(event: ConnectionEvent): State = withContext(dispatcher) {
        check(!closed) { "PeripheralContext is closed" }

        val previousState = _state.value
        val result = StateMachine.transition(previousState, event)
        if (!result.valid) {
            return@withContext previousState
        }

        val now = TimeSource.Monotonic.markNow()
        val durationInPrevious = stateEnteredAt?.let { now - it } ?: Duration.ZERO
        stateEnteredAt = now

        _state.value = result.newState
        logEvent(
            BleLogEvent.StateTransition(
                identifier = identifier,
                from = previousState,
                to = result.newState,
                durationInPreviousState = durationInPrevious,
            )
        )

        if (result.newState is State.Disconnected) {
            gattQueue.drain()
            _services.value = null
        }

        result.newState
    }

    suspend fun updateServices(discovered: List<DiscoveredService>) = withContext(dispatcher) {
        _services.value = discovered
    }

    suspend fun updateBondState(state: BondState) = withContext(dispatcher) {
        _bondState.value = state
    }

    suspend fun updateMtu(mtu: Int) = withContext(dispatcher) {
        _maximumWriteValueLength.value = (mtu - ATT_HEADER_SIZE).coerceAtLeast(DEFAULT_ATT_MTU - ATT_HEADER_SIZE)
    }

    /** Terminal — non-suspend for ViewModel.onCleared() / deinit. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        gattQueue.close()
        scope.cancel()
    }

    internal companion object {
        const val DEFAULT_ATT_MTU = 23
        const val ATT_HEADER_SIZE = 3
    }
}
