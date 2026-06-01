package com.atruedev.kmpble.scanner

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Collect [Scanner.scanEvents], return the first matching [predicate], or null after [timeout].
 *
 * **Warning:** [ScanEvent.Failed] events are silently skipped. If the scan hardware fails,
 * this returns null - indistinguishable from timeout or no matching device. Callers that
 * need to differentiate "timed out" from "scan failed" should use [firstOrThrow] or collect
 * [Scanner.scanEvents] directly to handle [ScanEvent.Failed] explicitly.
 *
 * Since [Scanner.scanEvents] is a cold flow, scanning starts when this function
 * begins collecting and stops automatically when a match is found or the timeout expires.
 *
 * ```
 * val ad = scanner.firstOrNull(timeout = 10.seconds) { it.name == "HeartSensor" }
 * ```
 */
public suspend fun Scanner.firstOrNull(
    timeout: Duration = 30.seconds,
    predicate: (Advertisement) -> Boolean = { true },
): Advertisement? =
    withTimeoutOrNull(timeout) {
        scanEvents
            .mapNotNull { event -> (event as? ScanEvent.Found)?.advertisement }
            .firstOrNull(predicate)
    }

/**
 * Collect [Scanner.scanEvents], return the first matching [predicate], or throw after [timeout].
 *
 * Unlike [firstOrNull], this surfaces [ScanEvent.Failed] as an exception so callers can
 * distinguish "timed out" from "scan hardware failed":
 *
 * ```
 * try {
 *     val ad = scanner.firstOrThrow(timeout = 10.seconds) { it.name == "HeartSensor" }
 * } catch (e: ScanFailedException) {
 *     // back off and retry
 * } catch (e: TimeoutCancellationException) {
 *     // no device found in time
 * }
 * ```
 */
public suspend fun Scanner.firstOrThrow(
    timeout: Duration = 30.seconds,
    predicate: (Advertisement) -> Boolean = { true },
): Advertisement =
    withTimeout(timeout) {
        scanEvents
            .map { event ->
                when (event) {
                    is ScanEvent.Found -> event.advertisement
                    is ScanEvent.Failed -> throw event.error
                }
            }.first(predicate)
    }
