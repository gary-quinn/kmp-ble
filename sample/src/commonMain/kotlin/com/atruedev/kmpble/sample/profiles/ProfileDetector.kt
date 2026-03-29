package com.atruedev.kmpble.sample.profiles

import com.atruedev.kmpble.sample.detail.ServiceUiModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object ProfileDetector {
    private val profileServiceUuids: Map<Uuid, ProfileType> =
        mapOf(
            Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb") to ProfileType.HEART_RATE,
            Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb") to ProfileType.BATTERY,
            Uuid.parse("00001810-0000-1000-8000-00805f9b34fb") to ProfileType.BLOOD_PRESSURE,
            Uuid.parse("00001808-0000-1000-8000-00805f9b34fb") to ProfileType.GLUCOSE,
            Uuid.parse("00001816-0000-1000-8000-00805f9b34fb") to ProfileType.CSC,
            Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb") to ProfileType.DEVICE_INFO,
        )

    fun detectProfiles(services: List<ServiceUiModel>): List<DetectedProfile> =
        services.mapNotNull { service ->
            profileServiceUuids[service.uuid]?.let { type ->
                DetectedProfile(type = type, serviceUuid = service.uuid)
            }
        }
}
