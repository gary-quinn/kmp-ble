package com.atruedev.kmpble.testing

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.internal.AbstractL2capChannel
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
import kotlinx.coroutines.Job

/**
 * Fake L2CAP channel for unit testing.
 *
 * Records all written data and allows injecting incoming data via [emitIncoming].
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
    }
}

internal class FakeL2capChannelBackend(
    psm: Int,
    mtu: Int,
    recovery: L2capRecoveryContext?,
    incomingBufferCapacity: Int = AbstractL2capChannel.DEFAULT_INCOMING_BUFFER,
) : AbstractL2capChannel(
        psm = psm,
        mtu = mtu,
        recovery = recovery,
        incomingBufferCapacity = incomingBufferCapacity,
    ) {
    private val writtenData = mutableListOf<ByteArray>()
    private val readJob = Job().apply { complete() }

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
        deliverIncoming(data)
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
