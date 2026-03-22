package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Collects latency samples and computes percentile statistics.
 * Not thread-safe — confine to a single coroutine or use external serialization.
 *
 * ```kotlin
 * val tracker = LatencyTracker()
 * repeat(50) {
 *     tracker.measure { peripheral.read(characteristic) }
 * }
 * val stats = tracker.summarize("GATT read")
 * println("p50=${stats.p50}, p95=${stats.p95}, p99=${stats.p99}")
 * ```
 */
@ExperimentalBleApi
public class LatencyTracker(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val samples = mutableListOf<Duration>()

    public suspend fun <T> measure(block: suspend () -> T): T {
        val mark = timeSource.markNow()
        val result = block()
        samples += mark.elapsedNow()
        return result
    }

    public fun record(duration: Duration) {
        samples += duration
    }

    public fun summarize(label: String): LatencyStats {
        val sorted = samples.sorted()
        return LatencyStats(
            label = label,
            count = sorted.size,
            min = sorted.firstOrNull() ?: Duration.ZERO,
            max = sorted.lastOrNull() ?: Duration.ZERO,
            mean = if (sorted.isEmpty()) Duration.ZERO
                   else sorted.fold(Duration.ZERO) { acc, d -> acc + d } / sorted.size,
            p50 = sorted.percentile(50),
            p95 = sorted.percentile(95),
            p99 = sorted.percentile(99),
        )
    }

    public fun reset() {
        samples.clear()
    }
}

// Nearest-rank percentile (C=0 interpolation) on a pre-sorted list.
private fun List<Duration>.percentile(p: Int): Duration {
    if (isEmpty()) return Duration.ZERO
    val index = ((p / 100.0) * (size - 1)).toInt().coerceIn(0, size - 1)
    return this[index]
}

/**
 * Statistical summary of latency measurements.
 */
@ExperimentalBleApi
public data class LatencyStats(
    val label: String,
    val count: Int,
    val min: Duration,
    val max: Duration,
    val mean: Duration,
    val p50: Duration,
    val p95: Duration,
    val p99: Duration,
) {
    override fun toString(): String =
        "$label ($count samples): min=$min, p50=$p50, p95=$p95, p99=$p99, max=$max"
}
