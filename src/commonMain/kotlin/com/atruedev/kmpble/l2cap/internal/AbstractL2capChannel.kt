package com.atruedev.kmpble.l2cap.internal

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capChannelState
import com.atruedev.kmpble.l2cap.L2capException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared lifecycle, state machine, error reporting, and incoming backpressure for L2CAP channels.
 */
internal abstract class AbstractL2capChannel(
    override val psm: Int,
    override val mtu: Int,
    private val recovery: L2capRecoveryContext?,
    incomingBufferCapacity: Int = DEFAULT_INCOMING_BUFFER,
) : L2capChannel {
  private val _state = MutableStateFlow(L2capChannelState.Opening)
    override val state: StateFlow<L2capChannelState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<L2capChannelError>(extraBufferCapacity = 16)
    override val errors: Flow<L2capChannelError> = _errors.asSharedFlow()

    private val closed = AtomicBoolean(false)
    private val closedDeferred = CompletableDeferred<Unit>()

    private var droppedPackets = 0

    protected val incomingChannel =
        Channel<ByteArray>(
            capacity = incomingBufferCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { _ ->
                droppedPackets++
                emitBackpressureOverflow()
            },
        )

    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    override val isOpen: Boolean
        get() = _state.value == L2capChannelState.Open

    internal suspend fun awaitClosed() {
        closedDeferred.await()
    }

    protected fun markOpen() {
        _state.value = L2capChannelState.Open
    }

    protected suspend fun deliverIncoming(data: ByteArray) {
        if (_state.value != L2capChannelState.Open) {
            _errors.emit(
                L2capChannelError.UnexpectedPacket(
                    psm = psm,
                    state = _state.value,
                ),
            )
            return
        }
        incomingChannel.send(data)
    }

    protected suspend fun emitError(error: L2capChannelError) {
        _errors.emit(error)
    }

    protected suspend fun failWith(error: L2capChannelError) {
        failWithSync(error)
    }

    protected fun failWithSync(error: L2capChannelError) {
        if (_state.value == L2capChannelState.Error || _state.value == L2capChannelState.Closed) return
        _state.value = L2capChannelState.Error
        _errors.tryEmit(error)
        finalizeClose(graceful = false)
    }

    protected fun finalizeClose(graceful: Boolean) {
        if (!closed.compareAndSet(false, true)) return

        if (_state.value != L2capChannelState.Error) {
            _state.value = L2capChannelState.Closing
        }

        if (graceful) {
            flushPendingWrites()
        }

        tearDownTransport()

        incomingChannel.close()
        if (_state.value != L2capChannelState.Error) {
            _state.value = L2capChannelState.Closed
        }
        closedDeferred.complete(Unit)
    }

    override fun close() {
        finalizeClose(graceful = true)
    }

    override suspend fun close(graceful: Boolean) {
        if (closed.get()) return
        finalizeClose(graceful = graceful)
    }

    override suspend fun recover(): L2capChannel {
        val ctx =
            recovery
                ?: throw L2capException.NotSupported("L2CAP channel recovery is not available for this channel")

        when (_state.value) {
            L2capChannelState.Error,
            L2capChannelState.Closed,
            -> Unit
            else ->
                throw L2capException.InvalidState(
                    "Cannot recover L2CAP channel in state ${_state.value}",
                )
        }

        if (!closed.get()) {
            finalizeClose(graceful = true)
        }

        return ctx.reopen()
    }

    /** Flush outbound data before closing streams. No-op when [graceful] is false. */
    protected open fun flushPendingWrites() {}

    /** Close platform sockets/streams. */
    protected abstract fun tearDownTransport()

    private fun emitBackpressureOverflow() {
        val dropped = droppedPackets
        _errors.tryEmit(
            L2capChannelError.BackpressureOverflow(
                psm = psm,
                state = _state.value,
                dropped = dropped,
            ),
        )
    }

    internal companion object {
        const val DEFAULT_INCOMING_BUFFER = 64
    }
}
