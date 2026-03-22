package com.atruedev.kmpble.profiles.glucose

import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull

private val GLUCOSE_MEASUREMENT_UUID = uuidFrom("2A18")
private val GLUCOSE_MEASUREMENT_CONTEXT_UUID = uuidFrom("2A34")
private val GLUCOSE_FEATURE_UUID = uuidFrom("2A51")

public fun Peripheral.glucoseMeasurements(
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<GlucoseMeasurement> {
    val char = findCharacteristic(ServiceUuid.GLUCOSE, GLUCOSE_MEASUREMENT_UUID)
        ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { parseGlucoseMeasurement(it) }
}

public fun Peripheral.glucoseMeasurementContexts(
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<GlucoseMeasurementContext> {
    val char = findCharacteristic(ServiceUuid.GLUCOSE, GLUCOSE_MEASUREMENT_CONTEXT_UUID)
        ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { parseGlucoseMeasurementContext(it) }
}

public suspend fun Peripheral.readGlucoseFeature(): GlucoseFeature? {
    val char = findCharacteristic(ServiceUuid.GLUCOSE, GLUCOSE_FEATURE_UUID) ?: return null
    return parseGlucoseFeature(read(char))
}
