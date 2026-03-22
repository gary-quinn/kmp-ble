package com.atruedev.kmpble.profiles.glucose

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class GlucoseMeasurementContext(
    val sequenceNumber: Int,
    val carbohydrateId: CarbohydrateId?,
    val carbohydrateWeight: Float?,
    val meal: Meal?,
    val tester: Tester?,
    val health: Health?,
    val exerciseDuration: Int?,
    val exerciseIntensity: Int?,
    val medicationId: MedicationId?,
    val medicationQuantity: Float?,
    val medicationUnit: MedicationUnit?,
    val hba1c: Float?,
)

public enum class CarbohydrateId {
    Reserved, Breakfast, Lunch, Dinner, Snack, Drink, Supper, Brunch;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved, 1 to Breakfast, 2 to Lunch, 3 to Dinner,
            4 to Snack, 5 to Drink, 6 to Supper, 7 to Brunch,
        )

        public fun fromByte(value: Int): CarbohydrateId? = mapping[value]
    }
}

public enum class Meal {
    Reserved, Preprandial, Postprandial, Fasting, Casual, Bedtime;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved, 1 to Preprandial, 2 to Postprandial,
            3 to Fasting, 4 to Casual, 5 to Bedtime,
        )

        public fun fromByte(value: Int): Meal? = mapping[value]
    }
}

public enum class Tester {
    Reserved, Self, HealthCareProfessional, LabTest, NotAvailable;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved,
            1 to Self,
            2 to HealthCareProfessional,
            3 to LabTest,
            15 to NotAvailable,
        )

        public fun fromNibble(value: Int): Tester? = mapping[value]
    }
}

public enum class Health {
    Reserved, MinorHealthIssues, MajorHealthIssues, DuringMenses,
    UnderStress, NoHealthIssues, NotAvailable;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved,
            1 to MinorHealthIssues,
            2 to MajorHealthIssues,
            3 to DuringMenses,
            4 to UnderStress,
            5 to NoHealthIssues,
            15 to NotAvailable,
        )

        public fun fromNibble(value: Int): Health? = mapping[value]
    }
}

public enum class MedicationId {
    Reserved, RapidActingInsulin, ShortActingInsulin, IntermediateActingInsulin,
    LongActingInsulin, PreMixedInsulin;

    public companion object {
        private val mapping = mapOf(
            0 to Reserved, 1 to RapidActingInsulin, 2 to ShortActingInsulin,
            3 to IntermediateActingInsulin, 4 to LongActingInsulin, 5 to PreMixedInsulin,
        )

        public fun fromByte(value: Int): MedicationId? = mapping[value]
    }
}

public enum class MedicationUnit { Kilograms, Liters }

public fun parseGlucoseMeasurementContext(data: ByteArray): GlucoseMeasurementContext? {
    val reader = BleByteReader(data)
    if (!reader.hasRemaining(3)) return null

    val flags = reader.readUInt8()
    val hasCarbohydrate = flags and 0x01 != 0
    val hasMeal = flags and 0x02 != 0
    val hasTesterHealth = flags and 0x04 != 0
    val hasExercise = flags and 0x08 != 0
    val hasMedication = flags and 0x10 != 0
    val medicationUnitIsLiters = flags and 0x20 != 0
    val hasHba1c = flags and 0x40 != 0

    val sequenceNumber = reader.readUInt16()

    // Extended flags byte is present if bit 7 is set (reserved, skip it)
    if (flags and 0x80 != 0) {
        if (!reader.hasRemaining(1)) return null
        reader.skip(1)
    }

    val carbohydrateId: CarbohydrateId?
    val carbohydrateWeight: Float?
    if (hasCarbohydrate) {
        if (!reader.hasRemaining(3)) return null
        carbohydrateId = CarbohydrateId.fromByte(reader.readUInt8())
        carbohydrateWeight = reader.readSFloat()
    } else {
        carbohydrateId = null
        carbohydrateWeight = null
    }

    val meal = if (hasMeal) {
        if (!reader.hasRemaining(1)) return null
        Meal.fromByte(reader.readUInt8())
    } else null

    val tester: Tester?
    val health: Health?
    if (hasTesterHealth) {
        if (!reader.hasRemaining(1)) return null
        val combined = reader.readUInt8()
        tester = Tester.fromNibble(combined and 0x0F)
        health = Health.fromNibble((combined shr 4) and 0x0F)
    } else {
        tester = null
        health = null
    }

    val exerciseDuration: Int?
    val exerciseIntensity: Int?
    if (hasExercise) {
        if (!reader.hasRemaining(3)) return null
        exerciseDuration = reader.readUInt16()
        exerciseIntensity = reader.readUInt8()
    } else {
        exerciseDuration = null
        exerciseIntensity = null
    }

    val medicationId: MedicationId?
    val medicationQuantity: Float?
    val medicationUnit: MedicationUnit?
    if (hasMedication) {
        if (!reader.hasRemaining(3)) return null
        medicationId = MedicationId.fromByte(reader.readUInt8())
        medicationQuantity = reader.readSFloat()
        medicationUnit = if (medicationUnitIsLiters) MedicationUnit.Liters else MedicationUnit.Kilograms
    } else {
        medicationId = null
        medicationQuantity = null
        medicationUnit = null
    }

    val hba1c = if (hasHba1c) {
        if (!reader.hasRemaining(2)) return null
        reader.readSFloat()
    } else null

    return GlucoseMeasurementContext(
        sequenceNumber = sequenceNumber,
        carbohydrateId = carbohydrateId,
        carbohydrateWeight = carbohydrateWeight,
        meal = meal,
        tester = tester,
        health = health,
        exerciseDuration = exerciseDuration,
        exerciseIntensity = exerciseIntensity,
        medicationId = medicationId,
        medicationQuantity = medicationQuantity,
        medicationUnit = medicationUnit,
        hba1c = hba1c,
    )
}
