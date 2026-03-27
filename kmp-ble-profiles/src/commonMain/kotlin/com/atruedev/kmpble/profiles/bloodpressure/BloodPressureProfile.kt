package com.atruedev.kmpble.profiles.bloodpressure

import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull

private val BP_MEASUREMENT_UUID = uuidFrom("2A35")
private val BP_FEATURE_UUID = uuidFrom("2A49")

/**
 * Observes Blood Pressure Measurement indications from the Blood Pressure Service (0x1810).
 *
 * @param backpressure Strategy for handling indications that arrive faster than the collector.
 * @return Flow of parsed [BloodPressureMeasurement] values, or an empty flow if the characteristic is absent.
 */
public fun Peripheral.bloodPressureMeasurements(
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<BloodPressureMeasurement> {
    val char = findCharacteristic(ServiceUuid.BLOOD_PRESSURE, BP_MEASUREMENT_UUID)
        ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { parseBloodPressureMeasurement(it) }
}

/** Reads the supported features of the Blood Pressure Service (0x1810). */
public suspend fun Peripheral.readBloodPressureFeature(): BloodPressureFeature? {
    val char = findCharacteristic(ServiceUuid.BLOOD_PRESSURE, BP_FEATURE_UUID) ?: return null
    return parseBloodPressureFeature(read(char))
}
