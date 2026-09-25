package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import org.bluez.exceptions.BluezFailedException
import org.bluez.exceptions.BluezInvalidOffsetException
import org.bluez.exceptions.BluezInvalidValueLengthException
import org.bluez.exceptions.BluezNotAuthorizedException
import org.bluez.exceptions.BluezNotConnectedException
import org.bluez.exceptions.BluezNotPermittedException
import org.bluez.exceptions.BluezNotSupportedException
import org.freedesktop.dbus.exceptions.DBusExecutionException

internal fun Throwable.toBlueZGattStatus(): GattStatus =
    when (this) {
        is BluezNotPermittedException -> GattStatus.WriteNotPermitted
        is BluezNotAuthorizedException -> GattStatus.InsufficientAuthorization
        is BluezInvalidOffsetException -> GattStatus.InvalidOffset
        is BluezInvalidValueLengthException -> GattStatus.InvalidAttributeLength
        is BluezNotSupportedException -> GattStatus.RequestNotSupported
        else -> GattStatus.Failure
    }

/**
 * Maps a BlueZ GATT failure to a common error. `NotConnected`, a D-Bus `UnknownObject`
 * (the device object vanished), or a disconnected bus mean the link is gone.
 */
internal fun Throwable.toBlueZGattError(operation: String): BleError =
    if (isBlueZLinkLoss()) {
        ConnectionLost("BlueZ link lost during $operation: $message", ConnectionFailureReason.LINK_LOSS)
    } else {
        GattError(operation, toBlueZGattStatus())
    }

internal fun Throwable.isBlueZLinkLoss(): Boolean =
    when (this) {
        is BluezNotConnectedException -> true
        is BluezFailedException -> message?.contains("Not connected", ignoreCase = true) == true
        is DBusExecutionException ->
            javaClass.name.endsWith("UnknownObject") ||
                javaClass.name.endsWith("NotConnected") ||
                message?.contains("Not connected", ignoreCase = true) == true
        else -> false
    }
