package com.atruedev.kmpble.backend

/** Byte stream of one open L2CAP connection-oriented channel. */
@KmpBleBackendApi
public interface L2capStream : AutoCloseable {
    public val psm: Int

    /** Maximum SDU size the stack reported, or a backend default. */
    public val mtu: Int

    /** Inbound data and closure are delivered in order; set before any data can be lost. */
    public fun setListener(listener: L2capStreamListener?)

    /** Suspends until [data] is handed to the stack. Fails with [com.atruedev.kmpble.l2cap.L2capException]. */
    public suspend fun write(data: ByteArray)

    override fun close()
}

@KmpBleBackendApi
public interface L2capStreamListener {
    public fun onData(data: ByteArray)

    /** The remote side or the stack closed the channel. [reason] is `null` for a clean end of stream. */
    public fun onClosed(reason: String?)
}

/** Publishes a local PSM and accepts inbound channels. */
@KmpBleBackendApi
public interface L2capListenerTransport : AutoCloseable {
    public fun setAcceptListener(listener: ((L2capStream) -> Unit)?)

    /** Returns the PSM the stack assigned. Fails with [com.atruedev.kmpble.l2cap.L2capException.PublishFailed]. */
    public suspend fun publish(secure: Boolean): Int

    override fun close()
}
