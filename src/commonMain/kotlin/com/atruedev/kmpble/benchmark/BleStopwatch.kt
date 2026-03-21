package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Measures elapsed time for BLE operations.
 *
 * ```kotlin
 * val stopwatch = BleStopwatch()
 * val result = stopwatch.measure("connect") {
 *     peripheral.connect(options)
 * }
 * println("${result.label}: ${result.duration}")
 * ```
 */
@ExperimentalBleApi
public class BleStopwatch(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /**
     * Measure the duration of a suspending operation.
     *
     * @param label descriptive name for the measurement
     * @param block the operation to time
     * @return [TimingResult] containing label, duration, and the operation's return value
     */
    public suspend fun <T> measure(label: String, block: suspend () -> T): TimingResult<T> {
        val mark = timeSource.markNow()
        val value = block()
        val elapsed = mark.elapsedNow()
        return TimingResult(label, elapsed, value)
    }
}

/**
 * Result of a single timed operation.
 */
@ExperimentalBleApi
public data class TimingResult<T>(
    val label: String,
    val duration: Duration,
    val value: T,
)
