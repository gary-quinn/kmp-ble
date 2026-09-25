package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.GattCharacteristicRecord
import com.atruedev.kmpble.backend.GattDescriptorRecord
import com.atruedev.kmpble.backend.GattServiceRecord
import com.atruedev.kmpble.gatt.Characteristic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Stable `Long` handles for BlueZ D-Bus object paths. Thread-safe. */
internal class BlueZHandleTable {
    private val next = AtomicLong(1)
    private val byPath = ConcurrentHashMap<String, Long>()
    private val byHandle = ConcurrentHashMap<Long, String>()

    fun handleFor(path: String): Long =
        byPath.computeIfAbsent(path) { next.getAndIncrement().also { handle -> byHandle[handle] = path } }

    fun pathFor(handle: Long): String? = byHandle[handle]

    fun handleOrNull(path: String): Long? = byPath[path]
}

@OptIn(ExperimentalUuidApi::class)
internal fun List<BlueZGattServiceSnapshot>.toRecords(handles: BlueZHandleTable): List<GattServiceRecord> =
    map { service ->
        GattServiceRecord(
            handle = handles.handleFor(service.path),
            uuid = normalizeUuid(service.uuid),
            characteristics =
                service.characteristics.map { characteristic ->
                    GattCharacteristicRecord(
                        handle = handles.handleFor(characteristic.path),
                        uuid = normalizeUuid(characteristic.uuid),
                        properties = flagsToProperties(characteristic.flags),
                        descriptors =
                            characteristic.descriptors.map { descriptor ->
                                GattDescriptorRecord(handles.handleFor(descriptor.path), normalizeUuid(descriptor.uuid))
                            },
                    )
                },
        )
    }

@OptIn(ExperimentalUuidApi::class)
internal fun normalizeUuid(raw: String): Uuid {
    val trimmed = raw.trim()
    return when {
        trimmed.contains('-') -> Uuid.parse(trimmed)
        trimmed.length <= 8 -> Uuid.parse("${trimmed.padStart(8, '0')}-0000-1000-8000-00805f9b34fb")
        else -> Uuid.parseHex(trimmed.padStart(32, '0'))
    }
}

/**
 * BlueZ `GattCharacteristic1.Flags` to common properties. `authenticated-signed-writes` is the
 * signed-write flag; `reliable-write` is an extended property and does not imply signing.
 */
internal fun flagsToProperties(flags: List<String>): Characteristic.Properties {
    val normalized = flags.map { it.lowercase() }.toSet()
    return Characteristic.Properties(
        read = "read" in normalized || "encrypt-read" in normalized || "encrypt-authenticated-read" in normalized,
        write = "write" in normalized || "encrypt-write" in normalized || "encrypt-authenticated-write" in normalized,
        writeWithoutResponse = "write-without-response" in normalized,
        signedWrite = "authenticated-signed-writes" in normalized,
        notify = "notify" in normalized,
        indicate = "indicate" in normalized,
    )
}
