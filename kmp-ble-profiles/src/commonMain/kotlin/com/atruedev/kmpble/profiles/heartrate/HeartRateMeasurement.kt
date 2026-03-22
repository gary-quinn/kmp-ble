package com.atruedev.kmpble.profiles.heartrate

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class HeartRateMeasurement(
    val heartRate: Int,
    val sensorContactDetected: Boolean?,
    val energyExpended: Int?,
    val rrIntervals: List<Int>,
)

public fun parseHeartRateMeasurement(data: ByteArray): HeartRateMeasurement? {
    val reader = BleByteReader(data)
    if (!reader.hasRemaining(2)) return null

    val flags = reader.readUInt8()
    val is16Bit = flags and 0x01 != 0
    val contactSupported = flags and 0x04 != 0
    val contactDetected = if (contactSupported) flags and 0x02 != 0 else null
    val hasEnergyExpended = flags and 0x08 != 0
    val hasRrIntervals = flags and 0x10 != 0

    val heartRate = if (is16Bit) {
        if (!reader.hasRemaining(2)) return null
        reader.readUInt16()
    } else {
        reader.readUInt8()
    }

    val energyExpended = if (hasEnergyExpended) {
        if (!reader.hasRemaining(2)) return null
        reader.readUInt16()
    } else null

    val rrIntervals = if (hasRrIntervals) {
        buildList {
            while (reader.hasRemaining(2)) {
                val raw = reader.readUInt16()
                add(raw * 1000 / 1024)
            }
        }
    } else emptyList()

    return HeartRateMeasurement(
        heartRate = heartRate,
        sensorContactDetected = contactDetected,
        energyExpended = energyExpended,
        rrIntervals = rrIntervals,
    )
}
