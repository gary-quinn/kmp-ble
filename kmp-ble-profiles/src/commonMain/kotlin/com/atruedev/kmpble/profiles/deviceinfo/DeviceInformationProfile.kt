package com.atruedev.kmpble.profiles.deviceinfo

import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

private val MANUFACTURER_NAME_UUID = uuidFrom("2A29")
private val MODEL_NUMBER_UUID = uuidFrom("2A24")
private val SERIAL_NUMBER_UUID = uuidFrom("2A25")
private val HARDWARE_REVISION_UUID = uuidFrom("2A27")
private val FIRMWARE_REVISION_UUID = uuidFrom("2A26")
private val SOFTWARE_REVISION_UUID = uuidFrom("2A28")
private val SYSTEM_ID_UUID = uuidFrom("2A23")
private val PNP_ID_UUID = uuidFrom("2A50")

public suspend fun Peripheral.readDeviceInformation(): DeviceInformation = coroutineScope {
    val svc = ServiceUuid.DEVICE_INFORMATION
    val manufacturerName = async { readStringChar(svc, MANUFACTURER_NAME_UUID) }
    val modelNumber = async { readStringChar(svc, MODEL_NUMBER_UUID) }
    val serialNumber = async { readStringChar(svc, SERIAL_NUMBER_UUID) }
    val hardwareRevision = async { readStringChar(svc, HARDWARE_REVISION_UUID) }
    val firmwareRevision = async { readStringChar(svc, FIRMWARE_REVISION_UUID) }
    val softwareRevision = async { readStringChar(svc, SOFTWARE_REVISION_UUID) }
    val systemId = async { readByteChar(svc, SYSTEM_ID_UUID)?.let(::parseSystemId) }
    val pnpId = async { readByteChar(svc, PNP_ID_UUID)?.let(::parsePnpId) }

    DeviceInformation(
        manufacturerName = manufacturerName.await(),
        modelNumber = modelNumber.await(),
        serialNumber = serialNumber.await(),
        hardwareRevision = hardwareRevision.await(),
        firmwareRevision = firmwareRevision.await(),
        softwareRevision = softwareRevision.await(),
        systemId = systemId.await(),
        pnpId = pnpId.await(),
    )
}

private suspend fun Peripheral.readStringChar(serviceUuid: Uuid, charUuid: Uuid): String? {
    val char = findCharacteristic(serviceUuid, charUuid) ?: return null
    val data = read(char)
    return if (data.isNotEmpty()) data.decodeToString() else null
}

private suspend fun Peripheral.readByteChar(serviceUuid: Uuid, charUuid: Uuid): ByteArray? {
    val char = findCharacteristic(serviceUuid, charUuid) ?: return null
    val data = read(char)
    return if (data.isEmpty()) null else data
}
