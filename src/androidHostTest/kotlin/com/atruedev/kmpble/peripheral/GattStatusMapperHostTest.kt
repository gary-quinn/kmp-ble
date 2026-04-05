package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothGatt
import com.atruedev.kmpble.error.GattStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GattStatusMapperHostTest {
    private val knownStatuses =
        listOf(
            BluetoothGatt.GATT_SUCCESS to GattStatus.Success,
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION to GattStatus.InsufficientAuthentication,
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION to GattStatus.InsufficientEncryption,
            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION to GattStatus.InsufficientAuthorization,
            BluetoothGatt.GATT_INVALID_OFFSET to GattStatus.InvalidOffset,
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH to GattStatus.InvalidAttributeLength,
            BluetoothGatt.GATT_READ_NOT_PERMITTED to GattStatus.ReadNotPermitted,
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED to GattStatus.WriteNotPermitted,
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED to GattStatus.RequestNotSupported,
            BluetoothGatt.GATT_CONNECTION_CONGESTED to GattStatus.ConnectionCongested,
            BluetoothGatt.GATT_FAILURE to GattStatus.Failure,
        )

    @Test
    fun `all known statuses round-trip without loss`() {
        for ((androidCode, expectedStatus) in knownStatuses) {
            val mapped = androidCode.toGattStatus()
            assertEquals(expectedStatus, mapped, "Android code $androidCode should map to $expectedStatus")

            val backToAndroid = mapped.toAndroidGattStatus()
            assertEquals(androidCode, backToAndroid, "$expectedStatus should map back to $androidCode")
        }
    }

    @Test
    fun `unknown status code maps to GattStatus Unknown`() {
        val unknownCode = 0x85
        val result = unknownCode.toGattStatus()
        assertIs<GattStatus.Unknown>(result)
        assertEquals(unknownCode, result.platformCode)
        assertEquals("android", result.platformName)
    }

    @Test
    fun `GattStatus Unknown round-trips via platformCode`() {
        val unknownCode = 0xFF
        val status = GattStatus.Unknown(platformCode = unknownCode, platformName = "android")
        val backToAndroid = status.toAndroidGattStatus()
        assertEquals(unknownCode, backToAndroid)
    }

    @Test
    fun `isSuccess returns true only for Success`() {
        assertEquals(true, GattStatus.Success.isSuccess())
        assertEquals(false, GattStatus.Failure.isSuccess())
        assertEquals(false, GattStatus.Unknown(0x85, "android").isSuccess())
    }
}
