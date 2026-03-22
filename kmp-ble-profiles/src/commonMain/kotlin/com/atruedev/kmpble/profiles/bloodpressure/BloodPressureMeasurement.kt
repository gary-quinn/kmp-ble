package com.atruedev.kmpble.profiles.bloodpressure

import com.atruedev.kmpble.profiles.parsing.BleByteReader
import com.atruedev.kmpble.profiles.parsing.BleDateTime

public data class BloodPressureMeasurement(
    val systolic: Float,
    val diastolic: Float,
    val meanArterialPressure: Float,
    val unit: BloodPressureUnit,
    val timestamp: BleDateTime?,
    val pulseRate: Float?,
    val userId: Int?,
    val measurementStatus: BloodPressureMeasurementStatus?,
)

public enum class BloodPressureUnit { MmHg, KPa }

public data class BloodPressureMeasurementStatus(
    val bodyMovementDetected: Boolean,
    val cuffTooLoose: Boolean,
    val irregularPulseDetected: Boolean,
    val pulseRateExceedsUpperLimit: Boolean,
    val pulseRateExceedsLowerLimit: Boolean,
    val improperMeasurementPosition: Boolean,
)

public fun parseBloodPressureMeasurement(data: ByteArray): BloodPressureMeasurement? {
    val reader = BleByteReader(data)
    if (!reader.hasRemaining(7)) return null

    val flags = reader.readUInt8()
    val unitIsKpa = flags and 0x01 != 0
    val hasTimestamp = flags and 0x02 != 0
    val hasPulseRate = flags and 0x04 != 0
    val hasUserId = flags and 0x08 != 0
    val hasMeasurementStatus = flags and 0x10 != 0

    val systolic = reader.readSFloat() ?: return null
    val diastolic = reader.readSFloat() ?: return null
    val map = reader.readSFloat() ?: return null

    val timestamp = if (hasTimestamp) {
        if (!reader.hasRemaining(7)) return null
        reader.readDateTime()
    } else null

    val pulseRate = if (hasPulseRate) {
        if (!reader.hasRemaining(2)) return null
        reader.readSFloat()
    } else null

    val userId = if (hasUserId) {
        if (!reader.hasRemaining(1)) return null
        reader.readUInt8()
    } else null

    val measurementStatus = if (hasMeasurementStatus) {
        if (!reader.hasRemaining(2)) return null
        val statusFlags = reader.readUInt16()
        BloodPressureMeasurementStatus(
            bodyMovementDetected = statusFlags and 0x01 != 0,
            cuffTooLoose = statusFlags and 0x02 != 0,
            irregularPulseDetected = statusFlags and 0x04 != 0,
            pulseRateExceedsUpperLimit = statusFlags and 0x08 != 0,
            pulseRateExceedsLowerLimit = statusFlags and 0x10 != 0,
            improperMeasurementPosition = statusFlags and 0x20 != 0,
        )
    } else null

    return BloodPressureMeasurement(
        systolic = systolic,
        diastolic = diastolic,
        meanArterialPressure = map,
        unit = if (unitIsKpa) BloodPressureUnit.KPa else BloodPressureUnit.MmHg,
        timestamp = timestamp,
        pulseRate = pulseRate,
        userId = userId,
        measurementStatus = measurementStatus,
    )
}
