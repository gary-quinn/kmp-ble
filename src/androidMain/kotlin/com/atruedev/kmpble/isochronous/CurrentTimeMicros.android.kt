package com.atruedev.kmpble.isochronous

/**
 * Android implementation of [currentTimeMicros] using [android.os.SystemClock.elapsedRealtimeNanos].
 *
 * Uses the monotonic elapsed real-time clock, immune to wall-clock changes.
 */
internal actual fun currentTimeMicros(): Long = android.os.SystemClock.elapsedRealtimeNanos() / 1_000L
