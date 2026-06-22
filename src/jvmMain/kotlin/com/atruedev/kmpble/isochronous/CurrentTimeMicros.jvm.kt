package com.atruedev.kmpble.isochronous

/**
 * JVM implementation of [currentTimeMicros] using [System.nanoTime].
 *
 * Uses the monotonic nanosecond clock converted to microseconds.
 * Suitable for relative timing within a single JVM session.
 */
internal actual fun currentTimeMicros(): Long = System.nanoTime() / 1_000L
