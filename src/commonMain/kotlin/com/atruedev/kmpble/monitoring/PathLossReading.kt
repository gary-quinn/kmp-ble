package com.atruedev.kmpble.monitoring

/**
 * A single path loss reading computed from TX power and RSSI.
 *
 * Path loss (dB) = [txPower] - [rssi]. This metric is used by BLE stacks
 * (Nordic SoftDevice, TI BLE5-Stack, ST BlueNRG) for adaptive frequency
 * hopping, power management, and distance estimation.
 */
public data class PathLossReading(
    /** Computed path loss in dB. */
    val pathLoss: Int,
    /** RSSI at time of reading in dBm. */
    val rssi: Int,
    /** Configured TX power level in dBm. */
    val txPower: Int,
)
