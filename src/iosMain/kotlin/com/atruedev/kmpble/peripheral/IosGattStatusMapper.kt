package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.GattStatus
import platform.CoreBluetooth.CBATTErrorInsufficientAuthentication
import platform.CoreBluetooth.CBATTErrorInsufficientAuthorization
import platform.CoreBluetooth.CBATTErrorInsufficientEncryption
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.Foundation.NSError

internal fun NSError?.toGattStatus(): GattStatus {
    if (this == null) return GattStatus.Success
    return when (code) {
        CBATTErrorSuccess -> GattStatus.Success
        CBATTErrorInsufficientAuthentication -> GattStatus.InsufficientAuthentication
        CBATTErrorInsufficientEncryption -> GattStatus.InsufficientEncryption
        CBATTErrorInsufficientAuthorization -> GattStatus.InsufficientAuthorization
        CBATTErrorInvalidOffset -> GattStatus.InvalidOffset
        CBATTErrorInvalidAttributeValueLength -> GattStatus.InvalidAttributeLength
        CBATTErrorReadNotPermitted -> GattStatus.ReadNotPermitted
        CBATTErrorWriteNotPermitted -> GattStatus.WriteNotPermitted
        CBATTErrorRequestNotSupported -> GattStatus.RequestNotSupported
        else -> GattStatus.Unknown(platformCode = code.toInt(), platformName = "ios")
    }
}

internal fun GattStatus.isSuccess(): Boolean = this == GattStatus.Success
