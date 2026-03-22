package com.atruedev.kmpble.testing

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

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
public class FakeL2capChannel(
    override val psm: Int,
    override val mtu: Int = 2048,
) : L2capChannel {
    private val incomingChannel = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming: Flow<ByteArray> = incomingChannel.consumeAsFlow()

    private var opened = true
    override val isOpen: Boolean get() = opened

    private val writtenData = mutableListOf<ByteArray>()

    override suspend fun write(data: ByteArray) {
        if (!opened) throw L2capException.ChannelClosed()
        writtenData.add(data.copyOf())
    }

    override fun close() {
        if (!opened) return
        opened = false
        incomingChannel.close()
    }

    /**
     * Inject incoming data as if received from the remote device.
     *
     * The most recent emission is replayed to late collectors (replay = 1),
     * so tests don't need to guarantee collector startup ordering.
     */
    public suspend fun emitIncoming(data: ByteArray) {
        incomingChannel.send(data)
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
}
