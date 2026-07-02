package com.atruedev.kmpble.monitoring

import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Monitors a [Peripheral]'s connection lifecycle and exposes aggregated
 * [ConnectionQuality] metrics via a [StateFlow].
 *
 * Launch monitoring with [start] and stop with [stop]. The monitor auto-tracks
 * connection/disconnection events through the peripheral's [Peripheral.state].
 *
 * ## Usage
 * ```
 * val monitor = ConnectionQualityMonitor(peripheral, scope)
 * monitor.start()
 * monitor.connectionQuality.collect { quality ->
 *     updateUi(quality)
 * }
 * ```
 */
public class ConnectionQualityMonitor(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope,
) {
    private val _connectionQuality = MutableStateFlow(ConnectionQuality())
    public val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    private var started = false
    private var collectionJob: Job? = null

    /**
     * Begin monitoring the peripheral's connection lifecycle.
     *
     * Safe to call multiple times - subsequent calls are no-ops.
     * Does not block; launches a coroutine in [scope] to observe state changes.
     */
    public fun start() {
        if (started) return
        started = true
        collectionJob =
            scope.launch {
                try {
                    peripheral.state.collect { state ->
                        updateFromState(state)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // State collection failed; leave last known quality snapshot intact
                }
            }
    }

    /**
     * Stop monitoring. The [connectionQuality] flow retains its last value.
     *
     * Cancels the active collection coroutine (does not cancel the scope).
     * Call [start] to resume monitoring.
     */
    public fun stop() {
        started = false
        collectionJob?.cancel()
        collectionJob = null
    }

    /**
     * Record an RSSI reading. Call after [Peripheral.readRssi] to feed
     * the result into the quality snapshot.
     */
    public fun recordRssi(rssi: Int) {
        _connectionQuality.update { it.copy(lastRssi = rssi) }
    }

    private fun updateFromState(state: State) {
        _connectionQuality.update { current ->
            val wasConnected = current.isConnected
            val isNowConnected = state is State.Connected

            when {
                isNowConnected && !wasConnected ->
                    current.copy(
                        totalConnections = current.totalConnections + 1,
                        isConnected = true,
                    )
                !isNowConnected && wasConnected ->
                    current.copy(
                        totalDisconnections = current.totalDisconnections + 1,
                        isConnected = false,
                    )
                else -> current.copy(isConnected = isNowConnected)
            }
        }
    }
}
