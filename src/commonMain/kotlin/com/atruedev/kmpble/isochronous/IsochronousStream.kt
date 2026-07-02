package com.atruedev.kmpble.isochronous

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * High-level streaming abstraction over a LE Audio Isochronous Channel.
 *
 * Wraps a raw [IsochronousChannel] with:
 * - Typed [IsochronousFrame] data instead of raw [ByteArray]
 * - Stream lifecycle state tracking via [state]
 * - Automatic channel lifecycle management
 * - Backpressure-compliant Flow-based API
 *
 * ## Usage
 *
 * ```kotlin
 * val channel = peripheral.openIsochronousChannel()
 * val stream = IsochronousStream.open(channel, IsochronousStreamConfig())
 *
 * stream.incoming.collect { frame ->
 *     audioProcessor.feed(frame.data, frame.timestamp)
 * }
 *
 * stream.send(audioData)
 * stream.close()
 * ```
 *
 * ## Stream States
 *
 * - [State.Idle] -- created but not yet collecting
 * - [State.Streaming] -- actively streaming
 * - [State.Closing] -- closing, draining buffers
 * - [State.Closed] -- fully closed
 * - [State.Failed] -- terminated due to issue
 *
 * ## Platform support
 *
 * Same as [IsochronousChannel]: not available on Android/iOS/JVM without
 * platform API support. The streaming API is defined for testing and future
 * platform adoption.
 */
public class IsochronousStream private constructor(
    private val channel: IsochronousChannel,
    private val config: IsochronousStreamConfig,
) : AutoCloseable {
    /**
     * A single data frame in the isochronous stream.
     */
    public data class IsochronousFrame(
        val data: ByteArray,
        val timestamp: Long,
        val sequenceNumber: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IsochronousFrame) return false
            return data.contentEquals(other.data) &&
                timestamp == other.timestamp &&
                sequenceNumber == other.sequenceNumber
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + sequenceNumber.hashCode()
            return result
        }

        override fun toString(): String = "IsochronousFrame(seq=$sequenceNumber, ts=$timestamp, len=${data.size})"
    }

    /**
     * Stream lifecycle states.
     */
    public enum class State {
        /** Initial state -- stream created but not yet started. */
        Idle,

        /** Stream is open and actively transporting data. */
        Streaming,

        /** Stream is closing -- final frames may be drained. */
        Closing,

        /** Stream is fully closed. No further operations allowed. */
        Closed,

        /** Stream terminated due to an unrecoverable issue. */
        Failed,
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    private var sequenceNumber: Long = 0L

    /**
     * Current stream lifecycle state.
     */
    public val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Whether the stream is currently open and streaming.
     */
    public val isStreaming: Boolean get() = _state.value == State.Streaming

    /**
     * Flow of incoming isochronous frames from the remote device.
     *
     * - Emits [IsochronousFrame] for each received SDU
     * - Automatically assigns timestamps and sequence numbers
     * - Completes when stream is closed or channel is lost
     * - Issues are reflected in [state] transitioning to [State.Failed]
     */
    public val incoming: Flow<IsochronousFrame> =
        channel.incoming
            .onStart { _state.value = State.Streaming }
            .buffer(config.bufferCapacity)
            .catch { cause ->
                if (cause !is CancellationException) {
                    _state.value = State.Failed
                    throw cause
                }
            }.onCompletion { cause ->
                if (cause == null && _state.value != State.Failed) {
                    _state.value = State.Closed
                }
            }.map { raw ->
                decodeFrame(raw)
                    ?: IsochronousFrame(
                        data = raw,
                        timestamp = 0L,
                        sequenceNumber = 0L,
                    )
            }

    /**
     * Send a data frame to the remote device.
     *
     * @param data Bytes to send.
     * @param timestamp Optional client timestamp in microseconds.
     * @throws IsochronousException if write fails or stream is closed
     */
    public suspend fun send(
        data: ByteArray,
        timestamp: Long = currentTimeMicros(),
    ) {
        if (_state.value == State.Closed || _state.value == State.Failed) {
            throw IsochronousException.ChannelClosed(
                "Stream is ${_state.value.name.lowercase()}",
            )
        }

        val frame =
            IsochronousFrame(
                data = data,
                timestamp = timestamp,
                sequenceNumber = ++sequenceNumber,
            )

        channel.write(encodeFrame(frame))
    }

    /**
     * Close the stream and underlying channel.
     *
     * Transitions state to [State.Closing], then [State.Closed].
     * Safe to call multiple times -- subsequent calls are no-ops.
     */
    override fun close() {
        if (_state.value == State.Closed ||
            _state.value == State.Failed ||
            _state.value == State.Closing
        ) {
            return
        }

        _state.value = State.Closing
        if (config.closeChannelOnClose) {
            channel.close()
        }
        // If no active collector, onCompletion won't fire.
        // Transition to Closed directly when collection is not in progress.
        if (_state.value == State.Closing) {
            _state.value = State.Closed
        }
    }

    /**
     * The MTU of the underlying channel.
     */
    public val mtu: Int get() = channel.mtu

    public companion object {
        /**
         * Open a stream on an existing [IsochronousChannel].
         *
         * @param channel The underlying isochronous channel (already opened).
         * @param config Stream configuration parameters.
         * @return A new [IsochronousStream] wrapping the channel.
         * @throws IsochronousException if the channel is not open
         */
        public fun open(
            channel: IsochronousChannel,
            config: IsochronousStreamConfig = IsochronousStreamConfig(),
        ): IsochronousStream {
            if (!channel.isOpen) {
                throw IsochronousException.NotConnected(
                    "Channel must be open before creating a stream",
                )
            }
            if (config.secure && !channel.isSecure) {
                throw IsochronousException.InvalidConfiguration(
                    "Stream requires secure transport but channel is not encrypted",
                )
            }
            return IsochronousStream(channel, config)
        }

        /**
         * Encode an [IsochronousFrame] into wire-format bytes.
         *
         * Wire format: [timestamp:8bytes BE][seqNum:8bytes BE][payload:remaining]
         */
        public fun encodeFrame(frame: IsochronousFrame): ByteArray {
            val result = ByteArray(16 + frame.data.size)
            // Timestamp (big-endian, 8 bytes)
            var ts = frame.timestamp
            for (i in 7 downTo 0) {
                result[i] = (ts and 0xFF).toByte()
                ts = ts shr 8
            }
            // Sequence number (big-endian, 8 bytes)
            var seq = frame.sequenceNumber
            for (i in 15 downTo 8) {
                result[i] = (seq and 0xFF).toByte()
                seq = seq shr 8
            }
            // Payload
            frame.data.copyInto(result, 16)
            return result
        }

        /**
         * Decode wire-format bytes into an [IsochronousFrame].
         *
         * Returns null if the byte array is too short (< 16 bytes header).
         */
        public fun decodeFrame(bytes: ByteArray): IsochronousFrame? {
            if (bytes.size < 16) return null
            var ts = 0L
            var seq = 0L
            for (i in 0..7) {
                ts = (ts shl 8) or (bytes[i].toLong() and 0xFF)
            }
            for (i in 8..15) {
                seq = (seq shl 8) or (bytes[i].toLong() and 0xFF)
            }
            val payload = bytes.copyOfRange(16, bytes.size)
            return IsochronousFrame(
                data = payload,
                timestamp = ts,
                sequenceNumber = seq,
            )
        }
    }
}

/**
 * Get the current time in microseconds.
 *
 * Uses system monotonic clock on each platform.
 * Override in tests with controlled time.
 */
internal expect fun currentTimeMicros(): Long
