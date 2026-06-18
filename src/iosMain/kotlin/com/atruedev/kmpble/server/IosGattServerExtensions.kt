package com.atruedev.kmpble.server

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.scanner.uuidFrom
import platform.CoreBluetooth.CBATTErrorInsufficientAuthentication
import platform.CoreBluetooth.CBATTErrorInsufficientAuthorization
import platform.CoreBluetooth.CBATTErrorInsufficientEncryption
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAttributePermissionsReadEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBUUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

// --- File-level constants ---

internal const val POWER_ON_TIMEOUT_MS = 10_000L
internal const val SERVICE_ADD_TIMEOUT_MS = 10_000L
internal const val MAX_NOTIFY_RETRIES = 3
internal val NOTIFY_TIMEOUT = 5.seconds
internal val DEFAULT_CENTRAL_IDLE_TIMEOUT = 5.minutes
internal val DEFAULT_CENTRAL_SWEEP_INTERVAL = 1.minutes
internal const val CB_ATT_ERROR_UNLIKELY: Long = 0x0E

// --- CBATTRequest / CBCentral extensions ---

internal val CBATTRequest.charUuid: Uuid get() = uuidFrom(characteristic.UUID.UUIDString)
internal val CBATTRequest.centralId: Identifier get() = Identifier(central.identifier.UUIDString)
internal val CBCentral.id: String get() = identifier.UUIDString

// --- Property / Permission builders ---

internal fun buildCBProperties(props: ServerCharacteristic.Properties): ULong {
    var flags: ULong = 0u
    if (props.read) flags = flags or CBCharacteristicPropertyRead
    if (props.write) flags = flags or CBCharacteristicPropertyWrite
    if (props.writeWithoutResponse) flags = flags or CBCharacteristicPropertyWriteWithoutResponse
    if (props.notify) flags = flags or CBCharacteristicPropertyNotify
    if (props.indicate) flags = flags or CBCharacteristicPropertyIndicate
    return flags
}

internal fun buildCBPermissions(perms: ServerCharacteristic.Permissions): ULong {
    var flags: ULong = 0u
    if (perms.read) flags = flags or CBAttributePermissionsReadable
    if (perms.readEncrypted) flags = flags or CBAttributePermissionsReadEncryptionRequired
    if (perms.write) flags = flags or CBAttributePermissionsWriteable
    if (perms.writeEncrypted) flags = flags or CBAttributePermissionsWriteEncryptionRequired
    return flags
}

internal fun buildNativeService(
    definition: ServiceDefinition,
    characteristicCache: MutableMap<Uuid, CBMutableCharacteristic>,
): CBMutableService {
    val service =
        CBMutableService(
            type = CBUUID.UUIDWithString(definition.uuid.toString()),
            primary = true,
        )
    val nativeChars: List<Any> =
        definition.characteristics.map { charDef ->
            val properties = buildCBProperties(charDef.properties)
            val permissions = buildCBPermissions(charDef.permissions)
            val char =
                CBMutableCharacteristic(
                    type = CBUUID.UUIDWithString(charDef.uuid.toString()),
                    properties = properties,
                    value = null,
                    permissions = permissions,
                )
            characteristicCache[charDef.uuid] = char
            char
        }
    service.setCharacteristics(nativeChars)
    return service
}

// --- GattStatus to CBATTError mapping ---

internal fun GattStatus.toCBATTError(): Long =
    when (this) {
        GattStatus.Success -> CBATTErrorSuccess
        GattStatus.ReadNotPermitted -> CBATTErrorReadNotPermitted
        GattStatus.WriteNotPermitted -> CBATTErrorWriteNotPermitted
        GattStatus.InvalidOffset -> CBATTErrorInvalidOffset
        GattStatus.InvalidAttributeLength -> CBATTErrorInvalidAttributeValueLength
        GattStatus.InsufficientAuthentication -> CBATTErrorInsufficientAuthentication
        GattStatus.InsufficientEncryption -> CBATTErrorInsufficientEncryption
        GattStatus.InsufficientAuthorization -> CBATTErrorInsufficientAuthorization
        GattStatus.RequestNotSupported -> CBATTErrorRequestNotSupported
        GattStatus.ConnectionCongested -> CB_ATT_ERROR_UNLIKELY
        GattStatus.Failure -> CB_ATT_ERROR_UNLIKELY
        is GattStatus.Unknown -> CB_ATT_ERROR_UNLIKELY
    }
