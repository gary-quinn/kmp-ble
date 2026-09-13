package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.GattStatus
import org.bluez.exceptions.BluezInvalidOffsetException
import org.bluez.exceptions.BluezInvalidValueLengthException
import org.bluez.exceptions.BluezNotAuthorizedException
import org.bluez.exceptions.BluezNotConnectedException
import org.bluez.exceptions.BluezNotPermittedException
import org.bluez.exceptions.BluezNotSupportedException

internal fun Throwable.toBlueZGattStatus(): GattStatus =
    when (this) {
        is BluezNotPermittedException -> GattStatus.WriteNotPermitted
        is BluezNotAuthorizedException -> GattStatus.InsufficientAuthorization
        is BluezInvalidOffsetException -> GattStatus.InvalidOffset
        is BluezInvalidValueLengthException -> GattStatus.InvalidAttributeLength
        is BluezNotSupportedException -> GattStatus.RequestNotSupported
        is BluezNotConnectedException -> GattStatus.Failure
        else -> GattStatus.Failure
    }

internal fun GattStatus.isSuccess(): Boolean = this == GattStatus.Success
