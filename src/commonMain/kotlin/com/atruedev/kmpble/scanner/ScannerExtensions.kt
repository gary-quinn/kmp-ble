package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.Identifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scan for [timeout], tracking each distinct peripheral at its best observed RSSI,
 * and return the strongest [limit] peripherals sorted strongest-first.
 *
 * Scanning runs for the full [timeout] window rather than stopping once [limit]
 * distinct peripherals are seen -- stopping early would bias results toward
 * whichever devices advertise first, which can miss a stronger signal that
 * arrives later. For "stop once I have N devices" semantics use [scanUntil].
 *
 * ```
 * val top5 = scanner.scanBatch(limit = 5, timeout = 15.seconds)
 * top5.forEach { println("${it.name} at ${it.rssi} dBm") }
 * ```
 *
 * [ScanEvent.Failed] events are skipped. Returns fewer than [limit] items (possibly
 * empty) if fewer distinct peripherals were seen within [timeout].
 */
public suspend fun Scanner.scanBatch(
    limit: Int,
    timeout: Duration = 30.seconds,
): List<Advertisement> {
    require(limit > 0) { "limit must be positive, was $limit" }
    val bestByRssi = HashMap<Identifier, Advertisement>()
    withTimeoutOrNull(timeout) {
        scanEvents.collect { event ->
            when (event) {
                is ScanEvent.Found -> {
                    val ad = event.advertisement
                    val current = bestByRssi[ad.identifier]
                    if (current == null || ad.rssi > current.rssi) {
                        bestByRssi[ad.identifier] = ad
                    }
                }
                is ScanEvent.Failed -> Unit
            }
        }
    }
    return bestByRssi.values.sortedByDescending { it.rssi }.take(limit)
}

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

/**
 * Collect [Scanner.scanEvents] until [predicate] matches, returning the first
 * matching [Advertisement], or `null` after [maxWait] elapses.
 *
 * General-purpose scan termination: unlike [firstOrNull], [maxWait] defaults to
 * [Duration.INFINITE], so this keeps scanning until the predicate matches. Combine
 * with any condition -- RSSI threshold, a set of expected MACs, service presence:
 *
 * ```
 * // scan until a strong-signal device appears (or never)
 * val strong = scanner.scanUntil { it.rssi > -60 }
 *
 * // scan until all 3 expected devices are seen (bounded)
 * val expected = setOf("AA:..", "BB:..", "CC:..")
 * val results = scanner.scanUntil(
 *     predicate = { it.identifier.value in expected },
 *     maxWait = 30.seconds,
 * )
 * ```
 *
 * [ScanEvent.Failed] events are skipped; a scan-hardware failure with an infinite
 * [maxWait] would suspend forever. Callers that need failure visibility should
 * collect [Scanner.scanEvents] directly.
 */
public suspend fun Scanner.scanUntil(
    maxWait: Duration = Duration.INFINITE,
    predicate: (Advertisement) -> Boolean,
): Advertisement? =
    withTimeoutOrNull(maxWait) {
        scanEvents
            .mapNotNull { event -> (event as? ScanEvent.Found)?.advertisement }
            .firstOrNull(predicate)
    }

/**
 * Collect [Scanner.scanEvents] until [count] distinct advertisements are seen
 * (or [maxWait] elapses), returning them as a list.
 *
 * Distinctness is by [Advertisement.identifier] -- repeated advertisements from
 * the same peripheral are deduplicated. Use this for "scan until I see N devices"
 * patterns:
 *
 * ```
 * val devices = scanner.scanUntil(count = 5, maxWait = 20.seconds)
 * ```
 *
 * Returns fewer than [count] items if [maxWait] expires first. [ScanEvent.Failed]
 * events are skipped (see [scanUntil] for the caveat with infinite waits).
 */
public suspend fun Scanner.scanUntil(
    count: Int,
    maxWait: Duration = Duration.INFINITE,
): List<Advertisement> {
    require(count > 0) { "count must be positive, was $count" }
    val seen = LinkedHashMap<Identifier, Advertisement>()
    try {
        withTimeoutOrNull(maxWait) {
            scanEvents.collect { event ->
                when (event) {
                    is ScanEvent.Found -> {
                        // First occurrence per identifier wins (true set-distinct,
                        // unlike distinctUntilChanged which only drops consecutive dupes).
                        seen.getOrPut(event.advertisement.identifier) { event.advertisement }
                        if (seen.size >= count) throw BatchComplete
                    }
                    is ScanEvent.Failed -> Unit
                }
            }
        }
    } catch (_: BatchComplete) {
        // count reached -- stop scanning and return what we have
    }
    // Partial results on timeout: return whatever was collected before maxWait.
    return seen.values.toList()
}

/** Internal signal to stop collection early once the target is reached. */
private object BatchComplete : CancellationException("scanUntil target reached")
