package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalBleApi::class)
class BenchmarkTest {

    @Test
    fun stopwatchMeasuresDuration() = runTest {
        val result = bleStopwatch("test-op") { 42 }
        assertEquals("test-op", result.label)
        assertEquals(42, result.value)
        assertTrue(result.duration >= 0.milliseconds)
    }

    @Test
    fun throughputMeterAccumulatesBytes() {
        val meter = ThroughputMeter()
        meter.start()
        meter.record(100)
        meter.record(200)
        meter.record(300)
        val result = meter.stop("transfer")
        assertEquals("transfer", result.label)
        assertEquals(600L, result.totalBytes)
        assertEquals(3L, result.sampleCount)
        assertEquals(200.0, result.averageSampleSize)
    }

    @Test
    fun throughputResultBytesPerSecond() {
        val result = ThroughputResult(
            label = "test",
            totalBytes = 1024,
            sampleCount = 1,
            duration = 1000.milliseconds,
        )
        assertEquals(1024.0, result.bytesPerSecond, 0.01)
    }

    @Test
    fun throughputResultZeroDuration() {
        val result = ThroughputResult(
            label = "test",
            totalBytes = 100,
            sampleCount = 1,
            duration = 0.milliseconds,
        )
        assertEquals(0.0, result.bytesPerSecond)
    }

    @Test
    fun latencyTrackerComputesStats() = runTest {
        val tracker = LatencyTracker()
        tracker.record(10.milliseconds)
        tracker.record(20.milliseconds)
        tracker.record(30.milliseconds)
        tracker.record(40.milliseconds)
        tracker.record(50.milliseconds)

        val stats = tracker.summarize("gatt-read")
        assertEquals("gatt-read", stats.label)
        assertEquals(5, stats.count)
        assertEquals(10.milliseconds, stats.min)
        assertEquals(50.milliseconds, stats.max)
        assertEquals(30.milliseconds, stats.mean)
        assertEquals(30.milliseconds, stats.p50)
    }

    @Test
    fun latencyTrackerMeasureBlock() = runTest {
        val tracker = LatencyTracker()
        val result = tracker.measure { "hello" }
        assertEquals("hello", result)

        val stats = tracker.summarize("op")
        assertEquals(1, stats.count)
    }

    @Test
    fun latencyTrackerEmptyStats() {
        val tracker = LatencyTracker()
        val stats = tracker.summarize("empty")
        assertEquals(0, stats.count)
        assertEquals(0.milliseconds, stats.min)
        assertEquals(0.milliseconds, stats.p50)
    }

    @Test
    fun latencyTrackerReset() = runTest {
        val tracker = LatencyTracker()
        tracker.record(10.milliseconds)
        tracker.reset()
        val stats = tracker.summarize("after-reset")
        assertEquals(0, stats.count)
    }

    @Test
    fun latencyTrackerPercentiles() {
        val tracker = LatencyTracker()
        for (i in 1..100) {
            tracker.record(i.milliseconds)
        }
        val stats = tracker.summarize("p-test")
        assertEquals(100, stats.count)
        assertEquals(1.milliseconds, stats.min)
        assertEquals(100.milliseconds, stats.max)
        assertEquals(50.milliseconds, stats.p50)
        assertEquals(95.milliseconds, stats.p95)
        assertEquals(99.milliseconds, stats.p99)
    }
}
