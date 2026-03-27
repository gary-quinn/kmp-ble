package com.atruedev.kmpble.profiles.heartrate

import com.atruedev.kmpble.profiles.parsing.BleByteReader

/**
 * Parsed Heart Rate Measurement (0x2A37) notification payload.
 *
 * @property heartRate Heart rate in beats per minute.
 * @property sensorContactDetected `true` if skin contact detected, `false` if not, `null` if unsupported.
 * @property energyExpended Cumulative energy in kilojoules, or `null` if not present.
 * @property rrIntervals R-R intervals converted from the raw 1/1024-second resolution to milliseconds.
 */
public data class HeartRateMeasurement(
    val heartRate: Int,
    val sensorContactDetected: Boolean?,
    val energyExpended: Int?,
    val rrIntervals: List<Int>,
)

/** Parses a Heart Rate Measurement characteristic value (0x2A37). */
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
