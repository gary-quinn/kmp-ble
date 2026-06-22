package com.atruedev.kmpble.direction

/**
 * Direction finding mode for Bluetooth 5.1+ Angle of Arrival / Angle of Departure.
 *
 * - [ANGLES_OF_ARRIVAL]: The receiver uses an antenna array to determine
 *   the direction of an incoming Constant Tone Extension (CTE) signal.
 * - [ANGLES_OF_DEPARTURE]: The transmitter switches between antennas while
 *   sending a CTE-enabled packet, and the receiver samples IQ data to
 *   compute the departure angle.
 */
public enum class DirectionFindingMode {
    ANGLES_OF_ARRIVAL,
    ANGLES_OF_DEPARTURE,
}
