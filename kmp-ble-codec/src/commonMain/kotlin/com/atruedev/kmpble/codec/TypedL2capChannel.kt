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
 * The underlying channel is intentionally not exposed. Collecting raw bytes
 * from it alongside [incoming] would split the byte stream between two
 * collectors and corrupt frames; writing raw bytes would bypass the framer
 * and produce a stream the peer cannot decode. Use a separate
 * [L2capListener.incoming] collection if raw access is required.
 *
 * `mtu`, `psm`, and `isOpen` forward to the underlying channel for
 * convenience. [close] closes the underlying channel.
 */
public class TypedL2capChannel<T> internal constructor(
    internal val channel: L2capChannel,
    public val incoming: Flow<T>,
    private val codec: BleCodec<T>,
    private val framer: Framer,
) : AutoCloseable {
    public val mtu: Int get() = channel.mtu
    public val psm: Int get() = channel.psm
    public val isOpen: Boolean get() = channel.isOpen

    /**
     * Encode [value] through the codec, frame the bytes, and write the
     * framed packet on the underlying channel.
     *
     * @throws FrameTooLargeException if the encoded payload exceeds the
     *   framer's `maxFrameSize`. No bytes are written in that case.
     */
    public suspend fun write(value: T): Unit = channel.writeFramed(value, codec, framer)

    override fun close(): Unit = channel.close()
}
