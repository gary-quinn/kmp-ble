package com.atruedev.kmpble.testing

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capChannelState
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
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
 * Fake L2CAP channel for unit testing.
 *
 * Records all written data and allows injecting incoming data via [emitIncoming].
 *
 * ## Example
 *
 * ```kotlin
 * val channel = FakeL2capChannel(psm = 0x25)
 *
 * // Simulate incoming data
 * channel.emitIncoming(byteArrayOf(0x01, 0x02))
 *
 * // Verify writes
 * channel.write(byteArrayOf(0x03))
 * assertEquals(1, channel.getWrittenData().size)
 * ```
 */
public class FakeL2capChannel internal constructor(
    override val psm: Int,
    override val mtu: Int = 2048,
    private val recovery: L2capRecoveryContext? = null,
) : L2capChannel {
    private val _state = MutableStateFlow(L2capChannelState.Open)
    override val state: StateFlow<L2capChannelState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<L2capChannelError>(extraBufferCapacity = 16)
    override val errors: Flow<L2capChannelError> = _errors.asSharedFlow()

    private val closed = AtomicBoolean(false)

    private val incomingChannel =
        Channel<ByteArray>(
            capacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    override val isOpen: Boolean get() = _state.value == L2capChannelState.Open

    private val writtenData = mutableListOf<ByteArray>()

    public constructor(psm: Int, mtu: Int = 2048) : this(psm, mtu, null)

    override suspend fun write(data: ByteArray) {
        if (!isOpen) throw L2capException.ChannelClosed()
        writtenData.add(data.copyOf())
    }

    override fun close() {
        closeInternal()
    }

    override suspend fun close(graceful: Boolean) {
        closeInternal()
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
            closeInternal()
        }

        return ctx.reopen()
    }

    /**
     * Inject incoming data as if received from the remote device.
     */
    public suspend fun emitIncoming(data: ByteArray) {
        if (!isOpen) {
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

    /**
     * Simulate a remote disconnect while the channel is open.
     */
    public suspend fun simulateRemoteDisconnect() {
        if (_state.value == L2capChannelState.Error || _state.value == L2capChannelState.Closed) return
        _state.value = L2capChannelState.Error
        _errors.emit(
            L2capChannelError.RemoteDisconnected(
                psm = psm,
                state = _state.value,
            ),
        )
        closeInternal()
    }

    /**
     * Get all data written via [write], in order.
     */
    public fun getWrittenData(): List<ByteArray> = writtenData.toList()

    /**
     * Clear recorded written data.
     */
    public fun clearWrittenData() {
        writtenData.clear()
    }

    private fun closeInternal() {
        if (!closed.compareAndSet(false, true)) return

        if (_state.value != L2capChannelState.Error) {
            _state.value = L2capChannelState.Closing
        }

        incomingChannel.close()
        if (_state.value != L2capChannelState.Error) {
            _state.value = L2capChannelState.Closed
        }
    }

    internal companion object {
        fun withRecovery(
            psm: Int,
            mtu: Int = 2048,
            reopen: suspend () -> L2capChannel,
        ): FakeL2capChannel =
            FakeL2capChannel(
                psm = psm,
                mtu = mtu,
                recovery =
                    L2capRecoveryContext(
                        psm = psm,
                        secure = true,
                        mtu = mtu,
                        reopen = reopen,
                    ),
            )
    }
}
