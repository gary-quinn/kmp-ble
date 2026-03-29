package com.atruedev.kmpble.sample.scanner

import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object DeviceCategoryClassifier {
    private val heartRateServiceUuid = uuidFrom("180d")
    private val batteryServiceUuid = uuidFrom("180f")
    private val bloodPressureServiceUuid = uuidFrom("1810")
    private val glucoseServiceUuid = uuidFrom("1808")
    private val cyclingSpeedServiceUuid = uuidFrom("1816")
    private val cyclingPowerServiceUuid = uuidFrom("1818")
    private val deviceInfoServiceUuid = uuidFrom("180a")
    private val nordicUartServiceUuid = uuidFrom("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    fun classify(serviceUuids: List<Uuid>): DeviceCategory =
        when {
            heartRateServiceUuid in serviceUuids -> DeviceCategory.HEART_RATE
            bloodPressureServiceUuid in serviceUuids -> DeviceCategory.BLOOD_PRESSURE
            glucoseServiceUuid in serviceUuids -> DeviceCategory.GLUCOSE
            cyclingSpeedServiceUuid in serviceUuids || cyclingPowerServiceUuid in serviceUuids -> DeviceCategory.CYCLING
            batteryServiceUuid in serviceUuids -> DeviceCategory.BATTERY
            nordicUartServiceUuid in serviceUuids -> DeviceCategory.NORDIC_DK
            deviceInfoServiceUuid in serviceUuids -> DeviceCategory.DEVICE_INFO
            else -> DeviceCategory.UNKNOWN
        }
}
