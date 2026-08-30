package com.atruedev.kmpble.testing

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capChannelState
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.internal.AbstractL2capChannel
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fake L2CAP channel for unit testing.
 *
 * Records all written data and allows injecting incoming data via [emitIncoming].
 * [incoming] replays the most recent packet to late collectors (replay = 1).
 */
public class FakeL2capChannel internal constructor(
    private val backend: FakeL2capChannelBackend,
) : L2capChannel by backend {
    public constructor(psm: Int, mtu: Int = 2048) : this(FakeL2capChannelBackend(psm, mtu, null))

    public suspend fun emitIncoming(data: ByteArray) {
        backend.emitIncoming(data)
    }

    public suspend fun simulateRemoteDisconnect() {
        backend.simulateRemoteDisconnect()
    }

    public fun getWrittenData(): List<ByteArray> = backend.getWrittenData()

    public fun clearWrittenData() {
        backend.clearWrittenData()
    }

    internal companion object {
        fun withRecovery(
            psm: Int,
            mtu: Int = 2048,
            reopen: suspend () -> L2capChannel,
        ): FakeL2capChannel =
            FakeL2capChannel(
                FakeL2capChannelBackend(
                    psm = psm,
                    mtu = mtu,
                    recovery = L2capRecoveryContext(reopen = reopen),
                ),
            )

        internal fun withSuspendIncomingBuffer(
            psm: Int,
            mtu: Int = 2048,
            incomingBufferCapacity: Int,
        ): FakeL2capChannel =
            FakeL2capChannel(
                FakeL2capChannelBackend(
                    psm = psm,
                    mtu = mtu,
                    recovery = null,
                    incomingBufferCapacity = incomingBufferCapacity,
                    useSuspendIncomingBuffer = true,
                ),
            )
    }
}

internal class FakeL2capChannelBackend(
    psm: Int,
    mtu: Int,
    recovery: L2capRecoveryContext?,
    incomingBufferCapacity: Int = AbstractL2capChannel.DEFAULT_INCOMING_BUFFER,
    private val useSuspendIncomingBuffer: Boolean = false,
) : AbstractL2capChannel(
        psm = psm,
        mtu = mtu,
        recovery = recovery,
        incomingBufferCapacity = incomingBufferCapacity,
    ) {
    private val writtenData = mutableListOf<ByteArray>()
    private val readJob = Job().apply { complete() }

    private val replayIncoming =
        MutableSharedFlow<ByteArray>(
            replay = 1,
            extraBufferCapacity = incomingBufferCapacity,
        )

    override val incoming: Flow<ByteArray> =
        if (useSuspendIncomingBuffer) {
            super.incoming
        } else {
            replayIncoming.asSharedFlow()
        }

    init {
        markOpen()
    }

    override suspend fun write(data: ByteArray) {
        if (!isOpen) throw L2capException.ChannelClosed()
        writtenData.add(data.copyOf())
    }

    override fun cancelReadJob() {
        readJob.cancel()
    }

    override fun tearDownTransport() {}

    suspend fun emitIncoming(data: ByteArray) {
        if (useSuspendIncomingBuffer) {
            deliverIncoming(data)
            return
        }
        if (state.value != L2capChannelState.Open) {
            emitError(
                L2capChannelError.UnexpectedPacket(
                    psm = psm,
                    state = state.value,
                ),
            )
            return
        }
        replayIncoming.emit(data)
    }

    suspend fun simulateRemoteDisconnect() {
        failWithSync(
            L2capChannelError.RemoteDisconnected(
                psm = psm,
                state = state.value,
            ),
        )
    }

    fun getWrittenData(): List<ByteArray> = writtenData.toList()

    fun clearWrittenData() {
        writtenData.clear()
    }
}
