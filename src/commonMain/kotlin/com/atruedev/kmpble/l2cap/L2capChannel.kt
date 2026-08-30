package com.atruedev.kmpble.l2cap

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
 * - [state] tracks [L2capChannelState] (Opening -> Open -> Closing -> Closed)
 * - [errors] emits [L2capChannelError] when the peer drops or data arrives on a closed channel
 * - Remote disconnect ends in [L2capChannelState.Closed]; subscribe to [errors] for the event
 * - [recover] reopens client channels when the last close was due to a recoverable error
 * - [incoming] completes normally when the channel closes (no exception)
 *
 * ## Recovery
 *
 * ```kotlin
 * var channel = peripheral.openL2capChannel(psm = 0x25)
 * channel.errors.collect { error ->
 *     if (error is L2capChannelError.RemoteDisconnected && error.recoverable) {
 *         channel = channel.recover() // returns a new channel; reassign your reference
 *     }
 * }
 * ```
 */
public interface L2capChannel : AutoCloseable {
    /**
     * The MTU (Maximum Transmission Unit) for this L2CAP CoC channel.
     *
     * Maximum payload size for a single write operation. Writes larger than
     * this are segmented automatically by the OS. Distinct from the GATT
     * ATT_MTU (default 23, max 517) - this is the L2CAP SDU MTU, typically
     * 2 KB to 64 KB depending on peripheral and connection.
     *
     * **Platform behavior:**
     * - **Android:** Queried from the socket via `maxTransmitPacketSize`, floored
     *   at 672 bytes. Reflects the actual negotiated value.
     * - **iOS:** CoreBluetooth does not expose the negotiated L2CAP MTU.
     *   Defaults to 2048 bytes. Pass an explicit `mtu` to
     *   [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel] to override
     *   when the peripheral's MTU is known (e.g., from a device specification).
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
     * Current channel lifecycle state.
     */
    public val state: StateFlow<L2capChannelState>

    /**
     * Structured recoverable and non-recoverable channel errors.
     */
    public val errors: Flow<L2capChannelError>

    /**
     * Flow of incoming data from the remote device.
     *
     * - Emits [ByteArray] for each received packet
     * - Completes normally when the channel is closed (locally or remotely)
     *
     * Backpressure: Buffered internally with suspend semantics. If the collector is slow,
     * the read loop suspends until the buffer has capacity, which stops draining the OS
     * stream buffer and triggers L2CAP flow control on the remote device. Packets are not
     * dropped silently.
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
     * Close the channel gracefully (flush pending writes).
     *
     * - Flushes any pending writes
     * - Closes underlying streams
     * - [incoming] flow completes
     * - Subsequent [write] calls throw [L2capException]
     *
     * Safe to call multiple times.
     */
    override fun close()

    /**
     * Close the channel, optionally skipping the outbound flush.
     *
     * @param graceful When true, flush pending writes before tearing down streams.
     */
    public suspend fun close(graceful: Boolean)

    /**
     * Reopen this channel after a recoverable remote disconnect.
     *
     * Only available for channels opened via
     * [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel] that reached
     * [L2capChannelState.Closed] due to a recoverable [L2capChannelError]. Graceful
     * [close] does not enable recovery. Server-side listener channels do not support recovery.
     *
     * Eligibility is tracked internally when a recoverable error closes the channel; you do
     * not need to have collected the matching event from [errors] before calling [recover].
     *
     * @return A new open channel to the same PSM with the same open parameters.
     * @throws L2capException.NotSupported when recovery context is unavailable.
     * @throws L2capException.InvalidState when the channel is not closed or was not recoverably closed.
     */
    public suspend fun recover(): L2capChannel
}
