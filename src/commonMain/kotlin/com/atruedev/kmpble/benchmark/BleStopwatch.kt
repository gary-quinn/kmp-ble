package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Measure the duration of a suspending BLE operation.
 *
 * ```kotlin
 * val result = bleStopwatch("connect") {
 *     peripheral.connect(options)
 * }
 * println("${result.label}: ${result.duration}")
 * ```
 */
@ExperimentalBleApi
public suspend fun <T> bleStopwatch(
    label: String,
    timeSource: TimeSource = TimeSource.Monotonic,
    block: suspend () -> T,
): TimingResult<T> {
    val mark = timeSource.markNow()
    val value = block()
    val elapsed = mark.elapsedNow()
    return TimingResult(label, elapsed, value)
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
