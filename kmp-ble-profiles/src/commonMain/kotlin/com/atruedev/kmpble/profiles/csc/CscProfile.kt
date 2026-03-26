package com.atruedev.kmpble.profiles.csc

import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull

private val CSC_MEASUREMENT_UUID = uuidFrom("2A5B")
private val CSC_FEATURE_UUID = uuidFrom("2A5C")

/**
 * Observes CSC Measurement notifications from the Cycling Speed and Cadence Service (0x1816).
 *
 * @param backpressure Strategy for handling notifications that arrive faster than the collector.
 * @return Flow of parsed [CscMeasurement] values, or an empty flow if the characteristic is absent.
 */
public fun Peripheral.cscMeasurements(
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<CscMeasurement> {
    val char = findCharacteristic(ServiceUuid.CYCLING_SPEED_AND_CADENCE, CSC_MEASUREMENT_UUID)
        ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { parseCscMeasurement(it) }
}

/** Reads the supported features of the Cycling Speed and Cadence Service (0x1816). */
public suspend fun Peripheral.readCscFeature(): CscFeature? {
    val char = findCharacteristic(ServiceUuid.CYCLING_SPEED_AND_CADENCE, CSC_FEATURE_UUID)
        ?: return null
    return parseCscFeature(read(char))
}
