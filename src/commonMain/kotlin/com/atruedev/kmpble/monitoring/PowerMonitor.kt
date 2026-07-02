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
 * Monitors path loss between a central and [Peripheral] by computing
 * `txPower - rssi` from RSSI readings.
 *
 * Path loss monitoring is a standard feature in BLE SDKs (Nordic
 * SoftDevice, TI BLE5-Stack, ST BlueNRG) used for adaptive frequency
 * hopping, dynamic TX power management, and proximity estimation.
 *
 * Since platform BLE APIs do not expose TX power directly, the monitor
 * uses a configurable [txPower] value (default 0 dBm, typical for BLE).
 * Callers that know their device's calibrated TX power should pass it.
 *
 * ## Usage
 * ```
 * val monitor = PowerMonitor(peripheral, scope, txPower = 4)
 * monitor.start()
 * // Periodically read RSSI and record:
 * val rssi = peripheral.readRssi()
 * monitor.recordRssi(rssi)
 * monitor.pathLoss.collect { reading -> updateProximity(reading) }
 * ```
 */
public class PowerMonitor(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope,
    /** Configured TX power level in dBm (default 0 dBm, typical BLE). */
    private val txPower: Int = 0,
) {
    private val _pathLoss = MutableStateFlow<PathLossReading?>(null)
    public val pathLoss: StateFlow<PathLossReading?> = _pathLoss.asStateFlow()

    private var started = false
    private var collectionJob: Job? = null

    /**
     * Begin monitoring the peripheral's connection lifecycle.
     *
     * Safe to call multiple times -- subsequent calls are no-ops.
     * Does not block; launches a coroutine in [scope] to observe
     * state changes and reset the reading on disconnect.
     */
    public fun start() {
        if (started) return
        started = true
        collectionJob =
            scope.launch {
                try {
                    peripheral.state.collect { state ->
                        if (state !is State.Connected) {
                            _pathLoss.update { null }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // State collection failed; leave last known reading intact
                }
            }
    }

    /**
     * Stop monitoring. The [pathLoss] flow retains its last value.
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
     * Record an RSSI reading and compute path loss.
     *
     * Call after [Peripheral.readRssi] to feed the result. Path loss
     * is computed as [txPower] - [rssi].
     */
    public fun recordRssi(rssi: Int) {
        _pathLoss.update {
            PathLossReading(
                pathLoss = txPower - rssi,
                rssi = rssi,
                txPower = txPower,
            )
        }
    }
}
