package com.atruedev.kmpble.l2cap

import kotlinx.coroutines.flow.Flow

/**
 * An L2CAP Connection-Oriented Channel for high-throughput streaming.
 *
 * L2CAP channels bypass GATT and provide stream-oriented communication
 * with higher throughput and lower latency. Use for firmware updates,
 * bulk data transfer, or any scenario where GATT's request/response
 * model is insufficient.
 *
 * ## Usage
 *
 * ```kotlin
 * val channel = peripheral.openL2capChannel(psm = 0x25)
 *
 * // Send data
 * channel.write(firmwareChunk)
 *
 * // Receive data
 * channel.incoming.collect { data ->
 *     processResponse(data)
 * }
 *
 * // Close when done
 * channel.close()
 * ```
 *
 * ## Lifecycle
 *
 * - Channel is opened via [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel]
 * - Channel remains open until [close] is called or connection is lost
 * - If the peripheral disconnects, the channel closes automatically
 * - [incoming] flow completes when channel closes
 */
public interface L2capChannel : AutoCloseable {

    /**
     * The MTU (Maximum Transmission Unit) for this channel.
     *
     * Maximum payload size for a single write operation.
     * Writes larger than this are segmented automatically by the OS.
     * Typical values: 2KB–64KB depending on peripheral and connection.
     *
     * **Note:** iOS does not expose the negotiated L2CAP MTU directly via
     * `CBL2CAPChannel`. The value returned is a conservative default (2048 bytes).
     * Callers performing chunked transfers (e.g., firmware updates) should treat
     * this as an upper-bound hint, not a precise negotiated value.
     */
    public val mtu: Int

    /**
     * The PSM (Protocol/Service Multiplexer) this channel is connected to.
     */
    public val psm: Int

    /**
     * Whether the channel is currently open and usable.
     */
    public val isOpen: Boolean

    /**
     * Flow of incoming data from the remote device.
     *
     * - Emits [ByteArray] for each received packet
     * - Completes normally when channel is closed (locally or remotely)
     * - Completes with exception if channel encounters an error
     *
     * Backpressure: Buffered internally. If the collector is slow, the
     * read loop suspends until the buffer has capacity, which in turn
     * stops draining the OS stream buffer and triggers L2CAP flow control
     * on the remote device.
     */
    public val incoming: Flow<ByteArray>

    /**
     * Write data to the channel.
     *
     * @param data The bytes to send. If larger than [mtu], the OS
     *             handles segmentation automatically.
     * @throws L2capException if write fails or channel is closed
     */
    public suspend fun write(data: ByteArray)

    /**
     * Close the channel.
     *
     * - Flushes any pending writes
     * - Closes underlying streams
     * - [incoming] flow completes
     * - Subsequent [write] calls throw [L2capException]
     *
     * Safe to call multiple times.
     */
    override fun close()
}
