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

    private val _maximumWriteValueLength = MutableStateFlow(20) // ATT_MTU 23 - 3
    val maximumWriteValueLength: StateFlow<Int> = _maximumWriteValueLength.asStateFlow()

    val gattQueue = GattOperationQueue(scope)

    private var closed = false

    /**
     * Process a state machine event. Always runs on the peripheral's serialized dispatcher.
     * Returns the new state. Invalid transitions are logged and ignored (no crash).
     */
    suspend fun processEvent(event: ConnectionEvent): State = withContext(dispatcher) {
        check(!closed) { "PeripheralContext is closed" }

        val previousState = _state.value
        val result = StateMachine.transition(previousState, event)
        if (!result.valid) {
            return@withContext previousState
        }

        _state.value = result.newState
        logEvent(BleLogEvent.StateTransition(identifier, from = previousState, to = result.newState))

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
        _maximumWriteValueLength.value = (mtu - 3).coerceAtLeast(20)
    }

    /**
     * Terminal — release all resources. Non-suspend so Peripheral.close() can be synchronous
     * (required for ViewModel.onCleared(), deinit, use {} blocks).
     */
    fun close() {
        if (closed) return
        closed = true
        gattQueue.close()
        scope.cancel()
    }
}
