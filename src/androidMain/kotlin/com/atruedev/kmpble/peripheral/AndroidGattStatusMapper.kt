package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothGatt
import com.atruedev.kmpble.error.GattStatus

internal fun Int.toGattStatus(): GattStatus =
    when (this) {
        BluetoothGatt.GATT_SUCCESS -> GattStatus.Success
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> GattStatus.InsufficientAuthentication
        BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> GattStatus.InsufficientEncryption
        BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION -> GattStatus.InsufficientAuthorization
        BluetoothGatt.GATT_INVALID_OFFSET -> GattStatus.InvalidOffset
        BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> GattStatus.InvalidAttributeLength
        BluetoothGatt.GATT_READ_NOT_PERMITTED -> GattStatus.ReadNotPermitted
        BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> GattStatus.WriteNotPermitted
        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> GattStatus.RequestNotSupported
        BluetoothGatt.GATT_CONNECTION_CONGESTED -> GattStatus.ConnectionCongested
        BluetoothGatt.GATT_FAILURE -> GattStatus.Failure
        else -> GattStatus.Unknown(platformCode = this, platformName = "android")
    }

internal fun GattStatus.isSuccess(): Boolean = this == GattStatus.Success

internal fun GattStatus.toAndroidGattStatus(): Int =
    when (this) {
        GattStatus.Success -> BluetoothGatt.GATT_SUCCESS
        GattStatus.InsufficientAuthentication -> BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
        GattStatus.InsufficientEncryption -> BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION
        GattStatus.InsufficientAuthorization -> BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION
        GattStatus.InvalidOffset -> BluetoothGatt.GATT_INVALID_OFFSET
        GattStatus.InvalidAttributeLength -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
        GattStatus.ReadNotPermitted -> BluetoothGatt.GATT_READ_NOT_PERMITTED
        GattStatus.WriteNotPermitted -> BluetoothGatt.GATT_WRITE_NOT_PERMITTED
        GattStatus.RequestNotSupported -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
        GattStatus.ConnectionCongested -> BluetoothGatt.GATT_CONNECTION_CONGESTED
        GattStatus.Failure -> BluetoothGatt.GATT_FAILURE
        is GattStatus.Unknown -> platformCode
    }
