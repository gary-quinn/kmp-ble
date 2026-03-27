package com.atruedev.kmpble.profiles.battery

import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull

private val BATTERY_LEVEL_UUID = uuidFrom("2A19")

/**
 * Reads the current battery level from the Battery Service (0x180F).
 *
 * @return Battery percentage (0–100), or `null` if the service or characteristic is absent.
 */
public suspend fun Peripheral.readBatteryLevel(): Int? {
    val char = findCharacteristic(ServiceUuid.BATTERY, BATTERY_LEVEL_UUID) ?: return null
    return parseBatteryLevel(read(char))
}

/**
 * Observes battery level notifications from the Battery Service (0x180F).
 *
 * @param backpressure Strategy for handling notifications that arrive faster than the collector.
 * @return Flow of battery percentages (0–100), or an empty flow if the characteristic is absent.
 */
public fun Peripheral.batteryLevelNotifications(
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<Int> {
    val char = findCharacteristic(ServiceUuid.BATTERY, BATTERY_LEVEL_UUID) ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { parseBatteryLevel(it) }
}
