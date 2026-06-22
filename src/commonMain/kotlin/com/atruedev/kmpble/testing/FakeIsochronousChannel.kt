package com.atruedev.kmpble.testing

import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.isochronous.IsochronousException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Fake [IsochronousChannel] for unit tests.
 *
 * Records writes and provides a controllable incoming data stream via
 * [emitIncoming]. Use in tests to simulate isochronous channel behavior
 * without platform dependencies.
 */
public class FakeIsochronousChannel(
    override val mtu: Int = 256,
    override val isSecure: Boolean = true,
) : IsochronousChannel {
    override var isOpen: Boolean = true
        private set

    private val _incoming = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming: Flow<ByteArray> = _incoming.receiveAsFlow()

    private val writtenData = mutableListOf<ByteArray>()

    override suspend fun write(data: ByteArray) {
        if (!isOpen) throw IsochronousException.ChannelClosed()
        writtenData.add(data)
    }

    override fun close() {
        isOpen = false
        _incoming.close()
    }

    /**
     * Simulate incoming data from the remote device.
     */
    public suspend fun emitIncoming(data: ByteArray) {
        _incoming.send(data)
    }

    /**
     * Retrieve all data written to this channel.
     */
    public fun getWrittenData(): List<ByteArray> = writtenData.toList()
}
