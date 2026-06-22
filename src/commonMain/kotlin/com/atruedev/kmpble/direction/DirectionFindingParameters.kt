package com.atruedev.kmpble.direction

/**
 * Parameters for requesting Bluetooth 5.1+ direction finding on a connection.
 *
 * Direction finding uses the Constant Tone Extension (CTE) transmitted at the
 * end of a BLE packet. The receiver samples IQ data from multiple antennas
 * to compute angle-of-arrival or angle-of-departure estimates.
 *
 * ## Parameter constraints (BT Core Spec v5.1, Vol 6, Part B, Section 2.5)
 *
 * - [mode]: Whether to use AoA or AoD.
 * - [cteLength]: Duration of the CTE in 8 us units. Valid range 2..20
 *   (16 us to 160 us). Longer CTE improves accuracy but consumes more power.
 * - [cteCount]: Number of CTE packets to sample per measurement cycle (1..16).
 *   Higher counts improve accuracy at the cost of measurement latency.
 * - [antennaConfig]: Physical antenna array layout and switching pattern.
 *
 * ## Platform support
 *
 * - **Android**: API 34+ via `BluetoothDevice#REQUEST_TYPE_DIRECTION_FINDING`.
 *   Older APIs or unsupported hardware return [DirectionFindingResult.NotSupported].
 * - **iOS**: CoreBluetooth does not expose a public direction finding API.
 *   Returns [DirectionFindingResult.NotSupported].
 *
 * @property mode The direction finding mode (AoA or AoD).
 * @property cteLength Constant Tone Extension length in 8 us units (2..20).
 * @property cteCount Number of CTE packets per measurement (1..16).
 * @property antennaConfig Antenna array configuration for IQ sampling.
 */
public data class DirectionFindingParameters(
    val mode: DirectionFindingMode,
    val cteLength: Int,
    val cteCount: Int,
    val antennaConfig: AntennaConfig,
) {
    init {
        require(cteLength in 2..20) {
            "cteLength must be 2..20 (16 us to 160 us), was $cteLength"
        }
        require(cteCount in 1..16) {
            "cteCount must be 1..16, was $cteCount"
        }
    }
}
