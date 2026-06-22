package com.atruedev.kmpble.isochronous

/**
 * Configuration for an [IsochronousStream].
 *
 * Controls stream behavior and optional quality-of-service parameters.
 * All parameters have sensible defaults suitable for typical LE Audio use.
 */
public data class IsochronousStreamConfig(
    /**
     * Maximum number of frames to buffer before applying backpressure.
     *
     * When the consumer is slower than the producer, frames accumulate
     * in the buffer. Once the buffer is full, the channel applies
     * backpressure to the remote device by not acknowledging frames.
     *
     * Default: 64 frames. For low-latency audio, use smaller values (8-16).
     */
    val bufferCapacity: Int = 64,
    /**
     * Whether to automatically close the underlying channel when the
     * stream is closed. When false, the caller must close the channel
     * separately.
     *
     * Default: true (stream owns channel lifecycle).
     */
    val closeChannelOnClose: Boolean = true,
    /**
     * Whether to require secure (encrypted) isochronous transport.
     *
     * LE Audio typically requires encryption. Set to false only for
     * testing or unencrypted broadcast scenarios.
     *
     * Default: true.
     */
    val secure: Boolean = true,
) {
    init {
        require(bufferCapacity > 0) {
            "bufferCapacity must be positive, got $bufferCapacity"
        }
    }
}
