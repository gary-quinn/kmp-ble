package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus

/** Decodes the native `status` field: 0, ATT error (1..255), `1000 + CBError`, or -1. */
internal object MacosStatus {
    private const val CB_ERROR_BASE = 1000
    private const val ATT_MAX = 0xFF

    const val CB_NOT_CONNECTED = 3
    const val CB_CONNECTION_TIMEOUT = 6
    const val CB_PERIPHERAL_DISCONNECTED = 7
    const val CB_CONNECTION_FAILED = 10
    const val CB_CONNECTION_LIMIT_REACHED = 11
    const val CB_UNKNOWN_DEVICE = 12
    const val CB_PEER_REMOVED_PAIRING = 14
    const val CB_ENCRYPTION_TIMED_OUT = 15

    fun isSuccess(status: Int): Boolean = status == 0

    fun cbError(status: Int): Int? = if (status >= CB_ERROR_BASE) status - CB_ERROR_BASE else null

    fun attError(status: Int): Int? = if (status in 1..ATT_MAX) status else null

    fun gattStatus(status: Int): GattStatus =
        when (attError(status)) {
            null -> if (status == 0) GattStatus.Success else GattStatus.Failure
            0x02 -> GattStatus.ReadNotPermitted
            0x03 -> GattStatus.WriteNotPermitted
            0x05 -> GattStatus.InsufficientAuthentication
            0x06 -> GattStatus.RequestNotSupported
            0x07 -> GattStatus.InvalidOffset
            0x08 -> GattStatus.InsufficientAuthorization
            0x0C -> GattStatus.InsufficientEncryption
            0x0D -> GattStatus.InvalidAttributeLength
            0x0E -> GattStatus.Failure
            0x0F -> GattStatus.InsufficientEncryption
            else -> GattStatus.Unknown(status, "ATT error 0x${status.toString(16)}")
        }

    /** `CBATTError` raw value for a [GattStatus] returned to a remote central. */
    fun attResult(status: GattStatus): Int =
        when (status) {
            GattStatus.Success -> 0x00
            GattStatus.ReadNotPermitted -> 0x02
            GattStatus.WriteNotPermitted -> 0x03
            GattStatus.InsufficientAuthentication -> 0x05
            GattStatus.RequestNotSupported -> 0x06
            GattStatus.InvalidOffset -> 0x07
            GattStatus.InsufficientAuthorization -> 0x08
            GattStatus.InvalidAttributeLength -> 0x0D
            GattStatus.InsufficientEncryption -> 0x0F
            GattStatus.ConnectionCongested, GattStatus.Failure -> 0x0E
            is GattStatus.Unknown -> status.platformCode.takeIf { it in 1..ATT_MAX } ?: 0x0E
        }

    fun isLinkLoss(status: Int): Boolean =
        cbError(status).let { it == CB_NOT_CONNECTED || it == CB_PERIPHERAL_DISCONNECTED }

    fun failureReason(status: Int): ConnectionFailureReason =
        when (cbError(status)) {
            CB_CONNECTION_TIMEOUT -> ConnectionFailureReason.TIMEOUT
            CB_PERIPHERAL_DISCONNECTED, CB_NOT_CONNECTED -> ConnectionFailureReason.LINK_LOSS
            CB_CONNECTION_FAILED -> ConnectionFailureReason.GATT_ERROR
            CB_CONNECTION_LIMIT_REACHED -> ConnectionFailureReason.CONNECTION_REJECTED
            CB_UNKNOWN_DEVICE -> ConnectionFailureReason.UNKNOWN_DEVICE
            CB_PEER_REMOVED_PAIRING, CB_ENCRYPTION_TIMED_OUT -> ConnectionFailureReason.AUTHENTICATION_FAILED
            else -> ConnectionFailureReason.UNKNOWN
        }

    fun gattError(
        operation: String,
        status: Int,
        message: String?,
    ): BleError =
        if (isLinkLoss(status)) {
            ConnectionLost(
                message ?: "Peripheral disconnected during $operation",
                ConnectionFailureReason.LINK_LOSS,
                status,
            )
        } else {
            GattError(operation, gattStatus(status))
        }

    fun connectError(
        status: Int,
        message: String?,
    ): BleError = ConnectionFailed(message ?: "CoreBluetooth connection failed", failureReason(status), status)

    fun disconnectError(
        status: Int,
        message: String?,
    ): BleError? = if (status == 0) null else ConnectionLost(message ?: "Disconnected", failureReason(status), status)
}
