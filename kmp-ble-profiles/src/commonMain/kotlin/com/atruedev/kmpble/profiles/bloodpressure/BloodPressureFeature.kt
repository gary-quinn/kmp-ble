package com.atruedev.kmpble.profiles.bloodpressure

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class BloodPressureFeature(
    val bodyMovementDetectionSupported: Boolean,
    val cuffFitDetectionSupported: Boolean,
    val irregularPulseDetectionSupported: Boolean,
    val pulseRateRangeDetectionSupported: Boolean,
    val measurementPositionDetectionSupported: Boolean,
    val multipleBondSupported: Boolean,
)

public fun parseBloodPressureFeature(data: ByteArray): BloodPressureFeature? {
    if (data.size < 2) return null
    val reader = BleByteReader(data)
    val flags = reader.readUInt16()
    return BloodPressureFeature(
        bodyMovementDetectionSupported = flags and 0x01 != 0,
        cuffFitDetectionSupported = flags and 0x02 != 0,
        irregularPulseDetectionSupported = flags and 0x04 != 0,
        pulseRateRangeDetectionSupported = flags and 0x08 != 0,
        measurementPositionDetectionSupported = flags and 0x10 != 0,
        multipleBondSupported = flags and 0x20 != 0,
    )
}
