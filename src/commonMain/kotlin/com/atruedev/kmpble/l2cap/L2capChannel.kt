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
 * - [state] tracks [L2capChannelState] transitions (Opening -> Open -> Closing/Error -> Closed)
 * - [errors] emits structured [L2capChannelError] for recoverable failures
 * - Channel remains open until [close] is called or connection is lost
 * - If the peripheral disconnects, the channel enters [L2capChannelState.Error] and
 *   [recover] may reopen it after reconnect
 * - [incoming] flow completes when channel closes
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
     * Reopen this channel after [L2capChannelState.Error] or [L2capChannelState.Closed].
     *
     * Only available for channels opened via
     * [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel]. Server-side listener
     * channels do not support recovery.
     *
     * @return A new open channel to the same PSM with the same open parameters.
     * @throws L2capException.NotSupported when recovery context is unavailable.
     * @throws L2capException.InvalidState when the channel is still opening or open.
     */
    public suspend fun recover(): L2capChannel
}
