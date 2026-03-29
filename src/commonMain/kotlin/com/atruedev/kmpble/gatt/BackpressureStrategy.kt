package com.atruedev.kmpble.gatt

/**
 * Strategy for handling backpressure when characteristic notifications arrive faster than
 * the collector can process them.
 */
public sealed class BackpressureStrategy {
    /** Drop intermediate values and keep only the most recent notification. */
    public data object Latest : BackpressureStrategy()

    /** Buffer up to [capacity] notifications before suspending the producer. */
    public data class Buffer(
        val capacity: Int,
    ) : BackpressureStrategy()

    /** Unlimited buffering — use with caution on high-throughput characteristics. */
    public data object Unbounded : BackpressureStrategy()
}
