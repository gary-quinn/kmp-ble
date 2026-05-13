package com.atruedev.kmpble.codec

import com.atruedev.kmpble.l2cap.L2capChannel
import kotlinx.coroutines.flow.Flow

/**
 * A typed, framed view over an [L2capChannel].
 *
 * Wraps an accepted L2CAP channel together with a [BleCodec] and a [Framer]
 * so that callers read and write typed values directly. Pairs with
 * [L2capListener.framedConnections]: the listener accepts raw channels and
 * each accepted channel is wrapped into a [TypedL2capChannel].
 *
 * The underlying [channel] is exposed for callers that need raw access
 * (custom byte writes, inspection). Properties [mtu], [psm], and [isOpen]
 * forward to it for convenience. [close] closes the underlying channel.
 */
public class TypedL2capChannel<T> internal constructor(
    public val channel: L2capChannel,
    public val incoming: Flow<T>,
    private val codec: BleCodec<T>,
    private val framer: Framer,
) : AutoCloseable {
    /** MTU of the underlying L2CAP channel. */
    public val mtu: Int get() = channel.mtu

    /** PSM of the underlying L2CAP channel. */
    public val psm: Int get() = channel.psm

    /** Whether the underlying L2CAP channel is open. */
    public val isOpen: Boolean get() = channel.isOpen

    /**
     * Encode [value] through the codec, frame the bytes, and write the
     * framed packet on the underlying channel. Throws if the encoded
     * payload exceeds the framer's `maxFrameSize`; no bytes are written
     * in that case.
     */
    public suspend fun write(value: T): Unit = channel.writeFramed(value, codec, framer)

    /** Close the underlying L2CAP channel. */
    override fun close(): Unit = channel.close()
}
