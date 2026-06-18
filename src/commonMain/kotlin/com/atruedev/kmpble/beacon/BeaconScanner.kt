package com.atruedev.kmpble.beacon

import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Filters a [Scanner] to emit only beacon-related scan events.
 *
 * Wraps a [Scanner] and emits [BeaconEvent] instances: either a parsed
 * [BeaconEvent.Found] or a forwarded [BeaconEvent.Failed] on scan errors.
 * Non-beacon advertisements are silently dropped.
 *
 * Follows the active-object pattern: call [start] to begin filtering,
 * [stop] to end. [beaconEvents] is a hot flow -- subscribe before
 * calling [start] to avoid missing events.
 *
 * ```kotlin
 * val scanner = PeripheralFactory.createScanner { filters { serviceUuids("feaa") } }
 * val beaconScanner = BeaconScanner(scanner, scope)
 *
 * scope.launch {
 *     beaconScanner.beaconEvents.collect { event ->
 *         when (event) {
 *             is BeaconEvent.Found -> handleBeacon(event.beacon)
 *             is BeaconEvent.Failed -> handleError(event.error)
 *         }
 *     }
 * }
 *
 * beaconScanner.start()
 * // ... scanning ...
 * beaconScanner.stop()
 * beaconScanner.close()
 * ```
 */
public class BeaconScanner(
    private val scanner: Scanner,
    private val scope: CoroutineScope,
) {
    private val _beaconEvents =
        MutableSharedFlow<BeaconEvent>(
            replay = 0,
            extraBufferCapacity = 64,
        )

    /** Hot flow of parsed beacon events. Subscribe before calling [start]. */
    public val beaconEvents: Flow<BeaconEvent> = _beaconEvents

    private var started: Boolean = false
    private var collectionJob: Job? = null

    /**
     * Begin filtering scan events for beacons.
     *
     * Idempotent: subsequent calls after the first are no-ops.
     */
    public fun start() {
        if (started) return
        started = true
        collectionJob =
            scanner.scanEvents
                .onEach { event ->
                    when (event) {
                        is ScanEvent.Found -> {
                            val beacon = event.advertisement.parseBeacon()
                            if (beacon != null) {
                                _beaconEvents.emit(BeaconEvent.Found(beacon))
                            }
                        }
                        is ScanEvent.Failed -> {
                            _beaconEvents.emit(BeaconEvent.Failed(event.error))
                        }
                    }
                }.launchIn(scope)
    }

    /**
     * Stop filtering scan events. The wrapped [Scanner] is not closed --
     * call [close] to release platform resources.
     *
     * Idempotent: subsequent calls after the first are no-ops.
     */
    public fun stop() {
        started = false
        collectionJob?.cancel()
        collectionJob = null
    }

    /**
     * Stop filtering and close the wrapped [Scanner], releasing platform
     * BLE resources.
     */
    public fun close() {
        stop()
        try {
            scanner.close()
        } catch (_: CancellationException) {
            // Propagate cancellation; close is best-effort
        } catch (_: Exception) {
            // Scanner close failures are non-fatal
        }
    }
}

/**
 * Event emitted by [BeaconScanner.beaconEvents].
 */
public sealed interface BeaconEvent {
    /** A beacon advertisement was parsed successfully. */
    public data class Found(
        val beacon: Beacon,
    ) : BeaconEvent

    /** The platform scan encountered an error. */
    public data class Failed(
        val error: Throwable,
    ) : BeaconEvent
}
