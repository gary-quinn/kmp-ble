package com.atruedev.kmpble.testing

import com.atruedev.kmpble.isochronous.IsochronousStream
import com.atruedev.kmpble.isochronous.IsochronousStreamConfig

/**
 * Controllable [IsochronousStream] for unit tests.
 *
 * Wraps a [FakeIsochronousChannel] and exposes simulation controls:
 * - [simulateIncomingFrame] -- deliver a frame to the receiver
 * - [simulateDisconnect] -- simulate channel loss
 * - [getSentFrames] -- inspect frames sent through the stream, decoded from channel writes
 *
 * Use [create] to construct a ready-to-use stream with a fake channel.
 */
public class FakeIsochronousStream private constructor(
    private val channel: FakeIsochronousChannel,
    config: IsochronousStreamConfig,
) {
    /** The underlying stream, ready to use. */
    public val stream: IsochronousStream = IsochronousStream.open(channel, config)

    /**
     * Simulate receiving a frame from the remote device.
     */
    public suspend fun simulateIncomingFrame(frame: IsochronousStream.IsochronousFrame) {
        channel.emitIncoming(IsochronousStream.encodeFrame(frame))
    }

    /**
     * Simulate the channel being disconnected.
     */
    public fun simulateDisconnect() {
        channel.close()
    }

    /**
     * Get all frames that were sent through this stream,
     * decoded from the underlying channel's written data.
     */
    public fun getSentFrames(): List<IsochronousStream.IsochronousFrame> =
        channel.getWrittenData().mapNotNull { IsochronousStream.decodeFrame(it) }

    public companion object {
        /**
         * Create a [FakeIsochronousStream] with a fake channel and default config.
         */
        public fun create(
            mtu: Int = 256,
            config: IsochronousStreamConfig = IsochronousStreamConfig(),
        ): FakeIsochronousStream = FakeIsochronousStream(FakeIsochronousChannel(mtu), config)
    }
}
