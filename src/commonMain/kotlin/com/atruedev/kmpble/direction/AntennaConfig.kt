package com.atruedev.kmpble.direction

/**
 * Antenna array configuration for Bluetooth 5.1+ direction finding.
 *
 * The antenna switch pattern defines the sequence in which antennas are
 * activated during the Constant Tone Extension. Platform controllers use
 * this to compute angle estimates from IQ samples.
 *
 * @property antennaSwitchPattern Ordered list of antenna indices (1-based)
 *   representing the switching pattern. For example, `[1, 2, 1, 2]` for a
 *   two-antenna array in alternating mode.
 * @property numberOfAntennas Total number of antennas in the array. Must
 *   match the maximum value in [antennaSwitchPattern].
 */
public data class AntennaConfig(
    val antennaSwitchPattern: List<Int>,
    val numberOfAntennas: Int,
) {
    init {
        require(antennaSwitchPattern.isNotEmpty()) {
            "antennaSwitchPattern must not be empty"
        }
        require(numberOfAntennas > 0) {
            "numberOfAntennas must be positive, was $numberOfAntennas"
        }
        val maxIndex = antennaSwitchPattern.maxOrNull() ?: 0
        require(maxIndex <= numberOfAntennas) {
            "antennaSwitchPattern references antenna $maxIndex but numberOfAntennas is $numberOfAntennas"
        }
        require(antennaSwitchPattern.all { it >= 1 }) {
            "antennaSwitchPattern indices must be 1-based, found: $antennaSwitchPattern"
        }
    }
}
