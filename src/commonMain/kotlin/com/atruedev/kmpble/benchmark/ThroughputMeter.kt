package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Measures data throughput for BLE transfer operations.
 *
 * Accumulates byte counts and computes throughput over the measurement window.
 * Not thread-safe — confine to a single coroutine or use external serialization.
 *
 * ```kotlin
 * val meter = ThroughputMeter()
 * meter.start()
 * repeat(100) {
 *     val data = peripheral.read(characteristic)
 *     meter.record(data.size)
 * }
 * val result = meter.stop("GATT reads")
 * println("${result.bytesPerSecond / 1024} KB/s")
 * ```
 */
@ExperimentalBleApi
public class ThroughputMeter(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var mark: TimeMark? = null
    private var totalBytes: Long = 0
    private var sampleCount: Long = 0

    public fun start() {
        mark = timeSource.markNow()
        totalBytes = 0
        sampleCount = 0
    }

    public fun record(bytes: Int) {
        totalBytes += bytes
        sampleCount++
    }

    public fun stop(label: String): ThroughputResult {
        val elapsed = mark?.elapsedNow() ?: Duration.ZERO
        return ThroughputResult(
            label = label,
            totalBytes = totalBytes,
            sampleCount = sampleCount,
            duration = elapsed,
        )
    }
}

/**
 * Result of a throughput measurement.
 */
@ExperimentalBleApi
public data class ThroughputResult(
    val label: String,
    val totalBytes: Long,
    val sampleCount: Long,
    val duration: Duration,
) {
    val bytesPerSecond: Double
        get() = if (duration.inWholeMilliseconds > 0) {
            totalBytes * 1000.0 / duration.inWholeMilliseconds
        } else 0.0

    val averageSampleSize: Double
        get() = if (sampleCount > 0) totalBytes.toDouble() / sampleCount else 0.0

    override fun toString(): String {
        val kbps = (bytesPerSecond / 1024 * 10).toLong() / 10.0
        return "$label: ${totalBytes}B in $duration, ${kbps} KB/s, $sampleCount samples"
    }
}
