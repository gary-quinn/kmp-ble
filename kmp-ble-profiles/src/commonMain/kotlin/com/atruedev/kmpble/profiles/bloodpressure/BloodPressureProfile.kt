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

public fun Peripheral.bloodPressureMeasurements(
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<BloodPressureMeasurement> {
    val char = findCharacteristic(ServiceUuid.BLOOD_PRESSURE, BP_MEASUREMENT_UUID)
        ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { parseBloodPressureMeasurement(it) }
}

public suspend fun Peripheral.readBloodPressureFeature(): BloodPressureFeature? {
    val char = findCharacteristic(ServiceUuid.BLOOD_PRESSURE, BP_FEATURE_UUID) ?: return null
    return parseBloodPressureFeature(read(char))
}
