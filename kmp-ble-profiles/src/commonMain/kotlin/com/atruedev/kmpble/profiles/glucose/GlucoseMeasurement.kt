package com.atruedev.kmpble.profiles.glucose

import com.atruedev.kmpble.profiles.parsing.BleByteReader
import com.atruedev.kmpble.profiles.parsing.BleDateTime

public data class GlucoseMeasurement(
    val sequenceNumber: Int,
    val baseTime: BleDateTime,
    val timeOffset: Int?,
    val concentration: Float?,
    val unit: GlucoseConcentrationUnit?,
    val type: GlucoseType?,
    val sampleLocation: GlucoseSampleLocation?,
    val sensorStatus: GlucoseSensorStatus?,
)

public enum class GlucoseConcentrationUnit { KgPerL, MolPerL }

public enum class GlucoseType {
    Reserved,
    CapillaryWholeBlood,
    CapillaryPlasma,
    VenousWholeBlood,
    VenousPlasma,
    ArterialWholeBlood,
    ArterialPlasma,
    UndeterminedWholeBlood,
    UndeterminedPlasma,
    InterstitialFluid,
    ControlSolution;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved,
            1 to CapillaryWholeBlood, 2 to CapillaryPlasma,
            3 to VenousWholeBlood, 4 to VenousPlasma,
            5 to ArterialWholeBlood, 6 to ArterialPlasma,
            7 to UndeterminedWholeBlood, 8 to UndeterminedPlasma,
            9 to InterstitialFluid, 10 to ControlSolution,
        )

        public fun fromNibble(value: Int): GlucoseType? = mapping[value]
    }
}

public enum class GlucoseSampleLocation {
    Reserved,
    Finger,
    AlternateSiteTest,
    Earlobe,
    ControlSolution,
    NotAvailable;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved,
            1 to Finger,
            2 to AlternateSiteTest,
            3 to Earlobe,
            4 to ControlSolution,
            15 to NotAvailable,
        )

        public fun fromNibble(value: Int): GlucoseSampleLocation? = mapping[value]
    }
}

public data class GlucoseSensorStatus(
    val batteryLow: Boolean,
    val sensorMalfunction: Boolean,
    val sampleSizeInsufficient: Boolean,
    val stripInsertionError: Boolean,
    val stripTypeIncorrect: Boolean,
    val sensorResultTooHigh: Boolean,
    val sensorResultTooLow: Boolean,
    val sensorTemperatureTooHigh: Boolean,
    val sensorTemperatureTooLow: Boolean,
    val sensorReadInterrupted: Boolean,
    val generalDeviceFault: Boolean,
    val timeFault: Boolean,
)

public fun parseGlucoseMeasurement(data: ByteArray): GlucoseMeasurement? {
    val reader = BleByteReader(data)
    if (!reader.hasRemaining(10)) return null

    val flags = reader.readUInt8()
    val hasTimeOffset = flags and 0x01 != 0
    val hasConcentration = flags and 0x02 != 0
    val concentrationUnitIsMol = flags and 0x04 != 0
    val hasSensorStatus = flags and 0x08 != 0

    val sequenceNumber = reader.readUInt16()
    val baseTime = reader.readDateTime()

    val timeOffset = if (hasTimeOffset) {
        if (!reader.hasRemaining(2)) return null
        reader.readInt16()
    } else null

    val concentration: Float?
    val unit: GlucoseConcentrationUnit?
    val type: GlucoseType?
    val sampleLocation: GlucoseSampleLocation?

    if (hasConcentration) {
        if (!reader.hasRemaining(3)) return null
        concentration = reader.readSFloat()
        val typeAndLocation = reader.readUInt8()
        type = GlucoseType.fromNibble(typeAndLocation and 0x0F)
        sampleLocation = GlucoseSampleLocation.fromNibble((typeAndLocation shr 4) and 0x0F)
        unit = if (concentrationUnitIsMol) GlucoseConcentrationUnit.MolPerL else GlucoseConcentrationUnit.KgPerL
    } else {
        concentration = null
        unit = null
        type = null
        sampleLocation = null
    }

    val sensorStatus = if (hasSensorStatus) {
        if (!reader.hasRemaining(2)) return null
        val statusFlags = reader.readUInt16()
        GlucoseSensorStatus(
            batteryLow = statusFlags and 0x0001 != 0,
            sensorMalfunction = statusFlags and 0x0002 != 0,
            sampleSizeInsufficient = statusFlags and 0x0004 != 0,
            stripInsertionError = statusFlags and 0x0008 != 0,
            stripTypeIncorrect = statusFlags and 0x0010 != 0,
            sensorResultTooHigh = statusFlags and 0x0020 != 0,
            sensorResultTooLow = statusFlags and 0x0040 != 0,
            sensorTemperatureTooHigh = statusFlags and 0x0080 != 0,
            sensorTemperatureTooLow = statusFlags and 0x0100 != 0,
            sensorReadInterrupted = statusFlags and 0x0200 != 0,
            generalDeviceFault = statusFlags and 0x0400 != 0,
            timeFault = statusFlags and 0x0800 != 0,
        )
    } else null

    return GlucoseMeasurement(
        sequenceNumber = sequenceNumber,
        baseTime = baseTime,
        timeOffset = timeOffset,
        concentration = concentration,
        unit = unit,
        type = type,
        sampleLocation = sampleLocation,
        sensorStatus = sensorStatus,
    )
}
