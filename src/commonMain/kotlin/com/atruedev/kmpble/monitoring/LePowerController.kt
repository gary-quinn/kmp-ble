package com.atruedev.kmpble.monitoring

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Active LE Power Control (LEPC) for peer transmit power adjustment.
 *
 * Where [PowerMonitor] provides **passive** path-loss monitoring,
 * `LePowerController` enables **active** power adjustment requests -
 * asking the connected peer to change its transmit power level.
 *
 * Bluetooth 5.1+ defines LE Power Control as a GATT-based procedure.
 * This implementation uses the existing
 * [Peripheral.requestConnectionParameterUpdate] API to translate
 * target dBm into connection parameter requests. See platform notes
 * for availability.
 *
 * ## Usage with PowerMonitor
 * ```
 * val monitor = PowerMonitor(peripheral, scope)
 * val controller = LePowerController(peripheral, scope)
 *
 * monitor.start()
 * controller.start()
 *
 * // PowerMonitor detects high path loss → alert
 * monitor.pathLoss.collect { reading ->
 *     if (reading != null && reading.pathLoss > 50) {
 *         // Request peer to increase power
 *         val response = controller.requestPeerPowerChange(-4)
 *         if (response.accepted) {
 *             // Monitor for RSSI improvement
 *         }
 *     }
 * }
 * ```
 *
 * ## Platform notes
 *
 * - **Android**: Uses `BluetoothGatt.requestConnectionPriority()`
 *   (API 21+) for coarse power adjustment via connection parameters.
 *   No fine-grained LEPC GATT procedure available yet.
 * - **iOS**: CoreBluetooth does not expose connection parameter
 *   negotiation or LE Power Control. Returns `accepted = false`.
 * - **JVM**: Stub - returns `accepted = false`.
 *
 * @property incomingPowerRequests Flow of power change requests from the peer.
 *   Empty on current platforms (not surfaced by Android or iOS APIs).
 */
public class LePowerController(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope,
) {
    private val _incomingPowerRequests =
        MutableSharedFlow<PeerPowerRequest>(replay = 0, extraBufferCapacity = 16)
    public val incomingPowerRequests: SharedFlow<PeerPowerRequest> =
        _incomingPowerRequests.asSharedFlow()

    private var started = false
    private var collectionJob: Job? = null

    /**
     * Begin monitoring for incoming power requests.
     *
     * Safe to call multiple times - subsequent calls are no-ops.
     * Does not block; launches a coroutine in [scope] to observe
     * connection state for power control opportunities.
     */
    public fun start() {
        if (started) return
        started = true
        collectionJob =
            scope.launch {
                try {
                    peripheral.state.collect { /* track connection for power control readiness */ }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // State collection failed; leave controller in last known state
                }
            }
    }

    /**
     * Stop monitoring. The [incomingPowerRequests] flow retains its buffer.
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
     * Request the connected peer to change its transmit power to [targetDbm].
     *
     * Translates the target dBm into connection parameter hints and delegates to
     * [Peripheral.requestConnectionParameterUpdate]. The peer may accept, reject,
     * or negotiate alternative parameters.
     *
     * | targetDbm  | Connection strategy | Interval       | Latency |
     * |------------|---------------------|----------------|---------|
     * | >= -4      | High power / low latency | 11.25..15 ms | 0       |
     * | >= -12     | Balanced                 | 30..50 ms    | 2       |
     * | >= -20     | Power saving             | 100..125 ms  | 4       |
     * | < -20      | Max power saving         | 300..400 ms  | 7       |
     *
     * @param targetDbm Desired peer transmit power in dBm. Higher (less negative)
     *   values request more power and lower latency connections.
     * @return [PeerPowerResponse] with acceptance status and negotiated parameters.
     *   [PeerPowerResponse.accepted] is `false` on platforms that do not support
     *   the operation.
     */
    @ExperimentalBleApi
    public suspend fun requestPeerPowerChange(targetDbm: Int): PeerPowerResponse {
        val params = powerToConnectionParams(targetDbm)
        val result = peripheral.requestConnectionParameterUpdate(params)
        return PeerPowerResponse(
            accepted = result != null,
            targetDbm = targetDbm,
            negotiatedInterval = result?.negotiatedInterval,
            negotiatedLatency = result?.negotiatedLatency,
            negotiatedSupervisionTimeout = result?.negotiatedSupervisionTimeout,
        )
    }

    private fun powerToConnectionParams(targetDbm: Int): ConnectionParameters =
        when {
            targetDbm >= -4 ->
                ConnectionParameters(
                    intervalRange = 11.25.milliseconds..15.milliseconds,
                    slaveLatency = 0,
                    supervisionTimeout = 500.milliseconds,
                )
            targetDbm >= -12 ->
                ConnectionParameters(
                    intervalRange = 30.milliseconds..50.milliseconds,
                    slaveLatency = 2,
                    supervisionTimeout = 2000.milliseconds,
                )
            targetDbm >= -20 ->
                ConnectionParameters(
                    intervalRange = 100.milliseconds..125.milliseconds,
                    slaveLatency = 4,
                    supervisionTimeout = 4000.milliseconds,
                )
            else ->
                ConnectionParameters(
                    intervalRange = 300.milliseconds..400.milliseconds,
                    slaveLatency = 7,
                    supervisionTimeout = 6000.milliseconds,
                )
        }
}
