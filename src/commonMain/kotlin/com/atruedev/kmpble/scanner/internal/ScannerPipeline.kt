package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanFailedException
import com.atruedev.kmpble.scanner.ScannerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform
import kotlin.time.TimeSource

/**
 * Converts a raw advertisement flow into a shared [ScanEvent] flow with filtering,
 * emission policy, and timeout applied.
 *
 * Both [AndroidScanner] and [IosScanner] use this as their scan pipeline.
 * Platform-specific code only needs to produce a [Flow]<[Advertisement]> and
 * pass it here.
 */
internal fun Flow<Advertisement>.toScanEvents(
    config: ScannerConfig,
    scope: CoroutineScope,
): Flow<ScanEvent> {
    val shared =
        map { ScanEvent.Found(it) as ScanEvent }
            .catch { e ->
                when (e) {
                    is ScanFailedException -> emit(ScanEvent.Failed(e))
                    else -> throw e
                }
            }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    var flow: Flow<ScanEvent> =
        shared
            .transform { event ->
                when (event) {
                    is ScanEvent.Found -> {
                        if (event.advertisement.matchesFilters(config.filterGroups)) {
                            emit(event)
                        }
                    }
                    is ScanEvent.Failed -> emit(event)
                }
            }.applyEmissionPolicy(config.emission)

    val timeout = config.timeout
    if (timeout != null) {
        val mark = TimeSource.Monotonic.markNow()
        flow = flow.takeWhile { mark.elapsedNow() < timeout }
    }

    return flow
}
