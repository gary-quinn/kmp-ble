package com.atruedev.kmpble.scanner

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Collect [advertisements], return the first matching [predicate], or null after [timeout].
 *
 * Since [Scanner.advertisements] is a cold flow, scanning starts when this function
 * begins collecting and stops automatically when a match is found or the timeout expires.
 *
 * ```
 * val ad = scanner.firstOrNull(timeout = 10.seconds) { it.name == "HeartSensor" }
 * ```
 */
public suspend fun Scanner.firstOrNull(
    timeout: Duration = 30.seconds,
    predicate: (Advertisement) -> Boolean = { true },
): Advertisement? {
    return withTimeoutOrNull(timeout) {
        advertisements.firstOrNull(predicate)
    }
}
