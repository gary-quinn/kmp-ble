package com.atruedev.kmpble.profiles.csc

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class CscMeasurement(
    val cumulativeWheelRevolutions: Long?,
    val lastWheelEventTime: Int?,
    val cumulativeCrankRevolutions: Int?,
    val lastCrankEventTime: Int?,
)

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
