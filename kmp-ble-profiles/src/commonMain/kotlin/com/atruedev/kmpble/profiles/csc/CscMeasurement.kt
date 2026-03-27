package com.atruedev.kmpble.profiles.csc

import com.atruedev.kmpble.profiles.parsing.BleByteReader

/**
 * Parsed Cycling Speed and Cadence Measurement (0x2A5B) notification payload.
 *
 * @property cumulativeWheelRevolutions Total wheel revolutions since reset, if present.
 * @property lastWheelEventTime Time of last wheel event (1/1024s resolution), if present.
 * @property cumulativeCrankRevolutions Total crank revolutions since reset, if present.
 * @property lastCrankEventTime Time of last crank event (1/1024s resolution), if present.
 */
public data class CscMeasurement(
    val cumulativeWheelRevolutions: Long?,
    val lastWheelEventTime: Int?,
    val cumulativeCrankRevolutions: Int?,
    val lastCrankEventTime: Int?,
)

/** Parses a CSC Measurement characteristic value (0x2A5B). */
public fun parseCscMeasurement(data: ByteArray): CscMeasurement? {
    val reader = BleByteReader(data)
    if (!reader.hasRemaining(1)) return null

    val flags = reader.readUInt8()
    val hasWheel = flags and 0x01 != 0
    val hasCrank = flags and 0x02 != 0

    val wheelRevolutions: Long?
    val lastWheelTime: Int?
    if (hasWheel) {
        if (!reader.hasRemaining(6)) return null
        wheelRevolutions = reader.readUInt32()
        lastWheelTime = reader.readUInt16()
    } else {
        wheelRevolutions = null
        lastWheelTime = null
    }

    val crankRevolutions: Int?
    val lastCrankTime: Int?
    if (hasCrank) {
        if (!reader.hasRemaining(4)) return null
        crankRevolutions = reader.readUInt16()
        lastCrankTime = reader.readUInt16()
    } else {
        crankRevolutions = null
        lastCrankTime = null
    }

    return CscMeasurement(
        cumulativeWheelRevolutions = wheelRevolutions,
        lastWheelEventTime = lastWheelTime,
        cumulativeCrankRevolutions = crankRevolutions,
        lastCrankEventTime = lastCrankTime,
    )
}
