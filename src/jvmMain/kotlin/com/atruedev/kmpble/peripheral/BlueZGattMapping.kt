package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun BlueZGattServiceSnapshot.toDiscoveredService(peripheral: BlueZPeripheral): DiscoveredService {
    val svcUuid = normalizeUuid(uuid)
    return DiscoveredService(
        uuid = svcUuid,
        characteristics =
            characteristics.map { snapshot ->
                val char = snapshot.toCharacteristic(svcUuid)
                peripheral.nativeCharPathMap[char] = snapshot.path
                char.descriptors.forEach { desc ->
                    val descPath = snapshot.descriptors.first { normalizeUuid(it.uuid) == desc.uuid }.path
                    peripheral.nativeDescPathMap[desc] = descPath
                }
                char
            },
    )
}

@OptIn(ExperimentalUuidApi::class)
internal fun BlueZGattCharacteristicSnapshot.toCharacteristic(serviceUuid: Uuid): Characteristic {
    val charUuid = normalizeUuid(uuid)
    val props = flagsToProperties(flags)
    val descs = mutableListOf<Descriptor>()
    val char = Characteristic(serviceUuid, charUuid, props, descs)
    descriptors.forEach { snapshot ->
        descs.add(Descriptor(char, normalizeUuid(snapshot.uuid)))
    }
    return char
}

@OptIn(ExperimentalUuidApi::class)
private fun normalizeUuid(raw: String): Uuid {
    val trimmed = raw.trim()
    return if (trimmed.contains('-')) {
        Uuid.parse(trimmed)
    } else {
        Uuid.parse(
            buildString {
                append(trimmed.padStart(8, '0').take(8))
                append('-')
                append(trimmed.drop(8).padStart(4, '0').take(4))
                append('-')
                append(trimmed.drop(12).padStart(4, '0').take(4))
                append('-')
                append(trimmed.drop(16).padStart(4, '0').take(4))
                append('-')
                append(trimmed.drop(20).padStart(12, '0').take(12))
            },
        )
    }
}

private fun flagsToProperties(flags: List<String>): Characteristic.Properties {
    val normalized = flags.map { it.lowercase() }.toSet()
    return Characteristic.Properties(
        read = "read" in normalized,
        write = "write" in normalized,
        writeWithoutResponse = "write-without-response" in normalized || "write-no-response" in normalized,
        signedWrite = "signed-write" in normalized || "reliable-write" in normalized,
        notify = "notify" in normalized,
        indicate = "indicate" in normalized,
    )
}
