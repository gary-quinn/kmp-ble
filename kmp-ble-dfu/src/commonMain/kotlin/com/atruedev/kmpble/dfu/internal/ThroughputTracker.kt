package com.atruedev.kmpble.dfu.internal

import kotlin.time.TimeSource

internal class ThroughputTracker(
    private val windowSize: Int = 10,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val timestamps = LongArray(windowSize)
    private val byteCounts = LongArray(windowSize)
    private var head = 0
    private var size = 0
    private val startMark = timeSource.markNow()

    fun record(bytesSent: Long) {
        val elapsed = startMark.elapsedNow().inWholeMilliseconds
        timestamps[head] = elapsed
        byteCounts[head] = bytesSent
        head = (head + 1) % windowSize
        if (size < windowSize) size++
    }

    fun bytesPerSecond(): Long {
        if (size < 2) return 0

        val oldestIdx = if (size < windowSize) 0 else head
        val newestIdx = (head - 1 + windowSize) % windowSize

        val durationMs = timestamps[newestIdx] - timestamps[oldestIdx]
        if (durationMs <= 0) return 0

        val deltaBytes = byteCounts[newestIdx] - byteCounts[oldestIdx]
        return (deltaBytes * 1000) / durationMs
    }
}
