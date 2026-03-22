package com.atruedev.kmpble.profiles.csc

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class CscFeature(
    val wheelRevolutionDataSupported: Boolean,
    val crankRevolutionDataSupported: Boolean,
    val multipleSensorLocationsSupported: Boolean,
)

public fun parseCscFeature(data: ByteArray): CscFeature? {
    if (data.size < 2) return null
    val reader = BleByteReader(data)
    val flags = reader.readUInt16()
    return CscFeature(
        wheelRevolutionDataSupported = flags and 0x01 != 0,
        crankRevolutionDataSupported = flags and 0x02 != 0,
        multipleSensorLocationsSupported = flags and 0x04 != 0,
    )
}
