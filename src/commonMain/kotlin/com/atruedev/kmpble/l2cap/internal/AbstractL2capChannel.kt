package com.atruedev.kmpble.l2cap.internal

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capChannelState
import com.atruedev.kmpble.l2cap.L2capException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Shared lifecycle, state machine, error reporting, and suspend-based incoming backpressure.
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

    private val closed = atomic(false)
    private val closedDeferred = CompletableDeferred<Unit>()
    private var recoverableAfterClose = false

    /**
     * Suspend-based buffer: when full, [deliverIncoming] suspends and the platform read loop
     * stops draining the OS stream, propagating L2CAP flow control to the peer.
     */
    protected val incomingChannel = Channel<ByteArray>(incomingBufferCapacity)

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

    protected fun failWithSync(error: L2capChannelError) {
        if (closed.value) return
        _errors.tryEmit(error)
        finalizeClose(graceful = false, recoverable = error.recoverable)
    }

    protected fun finalizeClose(
        graceful: Boolean,
        recoverable: Boolean = false,
    ) {
        if (!closed.compareAndSet(expect = false, update = true)) return

        recoverableAfterClose = recoverable

        if (_state.value == L2capChannelState.Open || _state.value == L2capChannelState.Opening) {
            _state.value = L2capChannelState.Closing
        }

        if (graceful) {
            flushPendingWrites()
        }

        tearDownTransport()

        incomingChannel.close()
        _state.value = L2capChannelState.Closed
        closedDeferred.complete(Unit)
    }

    override fun close() {
        cancelReadJob()
        finalizeClose(graceful = true)
    }

    override suspend fun close(graceful: Boolean) {
        if (closed.value) return
        cancelReadJob()
        finalizeClose(graceful = graceful)
    }

    override suspend fun recover(): L2capChannel {
        val ctx =
            recovery
                ?: throw L2capException.NotSupported("L2CAP channel recovery is not available for this channel")

        if (_state.value != L2capChannelState.Closed) {
            throw L2capException.InvalidState(
                "Cannot recover L2CAP channel in state ${_state.value}",
            )
        }

        if (!recoverableAfterClose) {
            throw L2capException.InvalidState(
                "Channel was closed without a recoverable error; recover() is not available",
            )
        }

        return ctx.reopen()
    }

    /** Cancel the platform read loop before tearing down transport. */
    protected abstract fun cancelReadJob()

    /** Flush outbound data before closing streams. No-op when [graceful] is false. */
    protected open fun flushPendingWrites() {}

    /** Close platform sockets/streams. */
    protected abstract fun tearDownTransport()

    internal companion object {
        const val DEFAULT_INCOMING_BUFFER = 64
    }
}
