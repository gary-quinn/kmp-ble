package com.atruedev.kmpble.isochronous

import kotlinx.coroutines.flow.Flow

/**
 * A LE Audio Isochronous Channel (CIS or BIS) for time-bounded streaming.
 *
 * Isochronous channels (Bluetooth 5.2+) provide connection-oriented or broadcast
 * streaming with guaranteed latency bounds, used by LE Audio profiles for
 * hearing aids, broadcast audio, and low-latency audio devices.
 *
 * ## CIS vs BIS
 *
 * - **CIS (Connected Isochronous Stream):** Bidirectional, connection-oriented
 *   stream between two devices. Part of a CIG (Connected Isochronous Group).
 * - **BIS (Broadcast Isochronous Stream):** Unidirectional broadcast stream
 *   from one device to many. Part of a BIG (Broadcast Isochronous Group).
 *
 * [IsochronousChannel] abstracts both types; the stream direction and timing
 * are negotiated during channel setup.
 *
 * ## Usage
 *
 * ```kotlin
 * val channel = peripheral.openIsochronousChannel()
 *
 * channel.incoming.collect { data ->
 *     audioProcessor.feed(data)
 * }
 *
 * channel.write(audioPacket)
 * channel.close()
 * ```
 *
 * ## Platform support
 *
 * - **Android:** Not publicly available (API 33+ `BluetoothLeAudio` handles
 *   isochronous channels internally; no raw channel API exposed to apps).
 *   Throws [IsochronousException.NotSupported].
 * - **iOS:** CoreBluetooth does not expose isochronous channels.
 *   Throws [IsochronousException.NotSupported].
 * - **JVM:** No Bluetooth stack. Throws [IsochronousException.NotSupported].
 *
 * ## Lifecycle
 *
 * - Channel is opened via [com.atruedev.kmpble.peripheral.Peripheral.openIsochronousChannel]
 * - Channel remains open until [close] is called or connection is lost
 * - If the peripheral disconnects, the channel closes automatically
 * - [incoming] flow completes when channel closes
 */
public interface IsochronousChannel : AutoCloseable {
    /**
     * The MTU (Maximum Transmission Unit) for this isochronous channel.
     *
     * Maximum payload size for a single SDU (Service Data Unit). The ISO
     * interval determines how many SDUs can be sent per ISO event.
     * Distinct from L2CAP MTU -- this is the ISO PDU size.
     *
     * **Platform behavior:**
     * - When the platform does not expose the negotiated value, a
     *   conservative default (e.g., 256 bytes) is used.
     */
    public val mtu: Int

    /**
     * Whether the channel is currently open and usable.
     */
    public val isOpen: Boolean

    /**
     * Whether the channel provides encrypted (secure) transport.
     *
     * LE Audio typically requires encryption. When false, data is
     * transmitted unencrypted (broadcast-only or test scenarios).
     */
    public val isSecure: Boolean

    /**
     * Flow of incoming data from the remote device.
     *
     * - Emits [ByteArray] for each received SDU
     * - Completes normally when channel is closed (locally or remotely)
     * - Completes with exception if channel encounters an error
     */
    public val incoming: Flow<ByteArray>

    /**
     * Write data to the channel.
     *
     * @param data The bytes to send. If larger than [mtu], the write
     *             may be fragmented or rejected depending on the platform.
     * @throws IsochronousException if write fails or channel is closed
     */
    public suspend fun write(data: ByteArray)

    /**
     * Close the channel.
     *
     * - Flushes any pending writes
     * - [incoming] flow completes
     * - Subsequent [write] calls throw [IsochronousException.ChannelClosed]
     *
     * Safe to call multiple times.
     */
    override fun close()
}
