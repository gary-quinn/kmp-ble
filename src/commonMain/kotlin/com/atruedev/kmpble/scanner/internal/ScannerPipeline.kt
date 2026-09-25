package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanFailedException
import com.atruedev.kmpble.scanner.ScannerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Converts a raw advertisement flow into a shared [ScanEvent] flow with filtering,
 * emission policy, and timeout applied.
 *
 * The Android, iOS, and JVM backend scanners use this as their scan pipeline.
 * Platform-specific code only needs to produce a [Flow]<[Advertisement]> and
 * pass it here.
 *
 * Each collection completes after a [ScanEvent.Failed] (the platform scan has ended) or once
 * [ScannerConfig.timeout] has elapsed since that collection started, whether or not anything
 * was found.
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

    val flow: Flow<ScanEvent> =
        shared
            .transformWhile { event ->
                when (event) {
                    is ScanEvent.Found -> {
                        if (event.advertisement.matchesFilters(config.filterGroups)) {
                            emit(event)
                        }
                    }
                    is ScanEvent.Failed -> emit(event)
                }
                event !is ScanEvent.Failed
            }.applyEmissionPolicy(config.emission)

    val timeout = config.timeout ?: return flow
    return flow.stopAfter(timeout)
}

private fun <T> Flow<T>.stopAfter(duration: Duration): Flow<T> {
    val upstream = this
    return channelFlow {
        val scan = launch { upstream.collect { send(it) } }
        val deadline =
            launch {
                delay(duration)
                scan.cancel()
            }
        scan.join()
        deadline.cancel()
    }.buffer(Channel.RENDEZVOUS)
}
