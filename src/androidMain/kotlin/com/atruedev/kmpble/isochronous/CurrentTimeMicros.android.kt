package com.atruedev.kmpble.isochronous

/**
 * Android implementation of [currentTimeMicros] using [System.nanoTime].
 *
 * Uses the JVM monotonic nanosecond clock converted to microseconds.
 * Available in both Android instrumented and host-side test environments.
 */
internal actual fun currentTimeMicros(): Long = System.nanoTime() / 1_000L
