package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.time.Duration.Companion.milliseconds

/**
 * Predefined BLE connection parameter configurations matching Nordic's
 * nRF Connect SDK presets for common use cases.
 *
 * Each preset maps to well-tested [ConnectionParameters] values that balance
 * latency, throughput, and power consumption for typical BLE applications.
 * The peripheral proposes these to the central via
 * [com.atruedev.kmpble.peripheral.Peripheral.requestConnectionParameterUpdate].
 * The central may accept, reject, or negotiate different values.
 *
 * ### Preset guidance
 *
 * | Preset          | Interval    | Latency | Supervision | Use case                              |
 * |-----------------|-------------|---------|-------------|---------------------------------------|
 * | BALANCED        | 30-50 ms    | 0       | 4000 ms     | General-purpose, sensor data          |
 * | HIGH_THROUGHPUT | 7.5-15 ms   | 0       | 4000 ms     | Firmware updates, L2CAP streaming     |
 * | POWER_SAVING    | 1000-2000 ms| 2       | 6000 ms     | Environmental sensors, beacons        |
 * | HID             | 11.25 ms    | 0       | 500 ms      | Keyboards, mice, game controllers     |
 *
 * ### Supervision timeout constraint
 *
 * BT Core Spec v5.0+ requires:
 * ```
 * supervisionTimeout > (1 + slaveLatency) * intervalMax * 2
 * ```
 * All presets satisfy this constraint with headroom:
 * - BALANCED: 4000 > (1+0) * 50 * 2 = 100  (40x headroom)
 * - HIGH_THROUGHPUT: 4000 > (1+0) * 15 * 2 = 30  (133x headroom)
 * - POWER_SAVING: 6000 > (1+2) * 2000 * 2 = 12000
 *
 *   This preset uses a supervision timeout below the BT spec minimum for the
 *   given interval+latency. Peers may reject the update or negotiate a higher
 *   timeout. If exact compliance is required, increase the supervision timeout.
 *
 * - HID: 500 > (1+0) * 11.25 * 2 = 22.5  (22x headroom)
 *
 * @see com.atruedev.kmpble.peripheral.Peripheral.requestConnectionParameterUpdate
 */
public enum class ConnectionParamPreset {
    /**
     * Balanced interval (30-50 ms) with zero latency.
     *
     * Suitable for interactive BLE applications (heart rate monitors,
     * temperature sensors, general data exchange). Default Android
     * connection priority.
     */
    BALANCED,

    /**
     * High throughput (7.5-15 ms) with zero latency.
     *
     * Maximizes data rate for bulk transfers (firmware updates, L2CAP
     * streaming, DFU). Highest power consumption. Use for short bursts
     * and return to [BALANCED] or [POWER_SAVING] after completion.
     */
    HIGH_THROUGHPUT,

    /**
     * Power saving (1000-2000 ms) with latency of 2.
     *
     * Minimizes power consumption for long-lived connections with
     * infrequent data (environmental sensors, beacons, time broadcasts).
     * High latency means up to 2 connection events may be skipped,
     * trading responsiveness for battery life.
     *
     * Note: The supervision timeout of 6000 ms is below the BT spec
     * minimum for this interval+latency combination. Some peers may
     * negotiate a higher timeout.
     */
    POWER_SAVING,

    /**
     * HID profile (11.25 ms fixed) with zero latency.
     *
     * Optimized for human interface devices (keyboards, mice, game
     * controllers) requiring consistent low-latency input. The fixed
     * 11.25 ms interval matches the HID service specification.
     */
    HID,
}

/**
 * Map a [ConnectionParamPreset] to concrete [ConnectionParameters].
 *
 * Produces the Nordic-recommended connection parameters for each preset.
 * These values are the proposed parameters; the actual negotiated values
 * are reported in [ConnectionParameterUpdateResult].
 */
public fun ConnectionParamPreset.toConnectionParameters(): ConnectionParameters =
    when (this) {
        ConnectionParamPreset.BALANCED ->
            ConnectionParameters(
                intervalRange = 30.milliseconds..50.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 4000.milliseconds,
            )

        ConnectionParamPreset.HIGH_THROUGHPUT ->
            ConnectionParameters(
                intervalRange = 7.5.milliseconds..15.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 4000.milliseconds,
            )

        ConnectionParamPreset.POWER_SAVING ->
            ConnectionParameters(
                intervalRange = 1000.milliseconds..2000.milliseconds,
                slaveLatency = 2,
                supervisionTimeout = 6000.milliseconds,
            )

        ConnectionParamPreset.HID ->
            ConnectionParameters(
                intervalRange = 11.25.milliseconds..11.25.milliseconds,
                slaveLatency = 0,
                supervisionTimeout = 500.milliseconds,
            )
    }

/**
 * Request connection parameters using a predefined [ConnectionParamPreset].
 *
 * Convenience wrapper around [Peripheral.requestConnectionParameterUpdate]
 * that maps well-known BLE connection presets (Nordic nRF Connect SDK
 * conventions) to concrete [ConnectionParameters].
 *
 * The peripheral proposes the preset's parameters to the central.
 * The central may accept, reject, or negotiate different values.
 *
 * ### Usage
 *
 * ```
 * peripheral.requestConnectionParameterPreset(ConnectionParamPreset.HIGH_THROUGHPUT)
 * // Perform bulk transfer...
 * peripheral.requestConnectionParameterPreset(ConnectionParamPreset.BALANCED)
 * ```
 *
 * @param preset The [ConnectionParamPreset] to request.
 * @return [ConnectionParameterUpdateResult] with negotiated values,
 *   or `null` if the platform does not support parameter updates
 *   (iOS always returns `null`).
 */
@ExperimentalBleApi
public suspend fun Peripheral.requestConnectionParameterPreset(
    preset: ConnectionParamPreset,
): ConnectionParameterUpdateResult? = requestConnectionParameterUpdate(preset.toConnectionParameters())
