package com.atruedev.kmpble.server

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.atruedev.kmpble.error.GattStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidGattServerTest {

    // =========================================================================
    // Properties mapping
    // =========================================================================

    @Test
    fun `properties map to Android int flags correctly - read`() {
        val props = ServerCharacteristic.Properties(read = true)
        assertEquals(BluetoothGattCharacteristic.PROPERTY_READ, props.toAndroidProperties())
    }

    @Test
    fun `properties map to Android int flags correctly - write`() {
        val props = ServerCharacteristic.Properties(write = true)
        assertEquals(BluetoothGattCharacteristic.PROPERTY_WRITE, props.toAndroidProperties())
    }

    @Test
    fun `properties map to Android int flags correctly - writeWithoutResponse`() {
        val props = ServerCharacteristic.Properties(writeWithoutResponse = true)
        assertEquals(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, props.toAndroidProperties())
    }

    @Test
    fun `properties map to Android int flags correctly - notify`() {
        val props = ServerCharacteristic.Properties(notify = true)
        assertEquals(BluetoothGattCharacteristic.PROPERTY_NOTIFY, props.toAndroidProperties())
    }

    @Test
    fun `properties map to Android int flags correctly - indicate`() {
        val props = ServerCharacteristic.Properties(indicate = true)
        assertEquals(BluetoothGattCharacteristic.PROPERTY_INDICATE, props.toAndroidProperties())
    }

    @Test
    fun `properties combine multiple flags correctly`() {
        val props = ServerCharacteristic.Properties(
            read = true,
            write = true,
            notify = true,
        )
        val expected = BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
        assertEquals(expected, props.toAndroidProperties())
    }

    @Test
    fun `empty properties returns zero`() {
        val props = ServerCharacteristic.Properties()
        assertEquals(0, props.toAndroidProperties())
    }

    // =========================================================================
    // Permissions mapping
    // =========================================================================

    @Test
    fun `permissions map to Android int flags correctly - read`() {
        val perms = ServerCharacteristic.Permissions(read = true)
        assertEquals(BluetoothGattCharacteristic.PERMISSION_READ, perms.toAndroidPermissions())
    }

    @Test
    fun `permissions map to Android int flags correctly - readEncrypted`() {
        val perms = ServerCharacteristic.Permissions(readEncrypted = true)
        assertEquals(BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED, perms.toAndroidPermissions())
    }

    @Test
    fun `permissions map to Android int flags correctly - write`() {
        val perms = ServerCharacteristic.Permissions(write = true)
        assertEquals(BluetoothGattCharacteristic.PERMISSION_WRITE, perms.toAndroidPermissions())
    }

    @Test
    fun `permissions map to Android int flags correctly - writeEncrypted`() {
        val perms = ServerCharacteristic.Permissions(writeEncrypted = true)
        assertEquals(BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED, perms.toAndroidPermissions())
    }

    @Test
    fun `permissions combine multiple flags correctly`() {
        val perms = ServerCharacteristic.Permissions(
            read = true,
            write = true,
        )
        val expected = BluetoothGattCharacteristic.PERMISSION_READ or
            BluetoothGattCharacteristic.PERMISSION_WRITE
        assertEquals(expected, perms.toAndroidPermissions())
    }

    @Test
    fun `empty permissions returns zero`() {
        val perms = ServerCharacteristic.Permissions()
        assertEquals(0, perms.toAndroidPermissions())
    }

    // =========================================================================
    // GattStatus to Android int mapping
    // =========================================================================

    @Test
    fun `GattStatus Success maps to GATT_SUCCESS`() {
        assertEquals(BluetoothGatt.GATT_SUCCESS, GattStatus.Success.toAndroidGattStatus())
    }

    @Test
    fun `GattStatus Failure maps to GATT_FAILURE`() {
        assertEquals(BluetoothGatt.GATT_FAILURE, GattStatus.Failure.toAndroidGattStatus())
    }

    @Test
    fun `GattStatus ReadNotPermitted maps to GATT_READ_NOT_PERMITTED`() {
        assertEquals(BluetoothGatt.GATT_READ_NOT_PERMITTED, GattStatus.ReadNotPermitted.toAndroidGattStatus())
    }

    @Test
    fun `GattStatus WriteNotPermitted maps to GATT_WRITE_NOT_PERMITTED`() {
        assertEquals(BluetoothGatt.GATT_WRITE_NOT_PERMITTED, GattStatus.WriteNotPermitted.toAndroidGattStatus())
    }

    @Test
    fun `GattStatus RequestNotSupported maps to GATT_REQUEST_NOT_SUPPORTED`() {
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, GattStatus.RequestNotSupported.toAndroidGattStatus())
    }

    @Test
    fun `GattStatus Unknown preserves platform code`() {
        val status = GattStatus.Unknown(platformCode = 42, platformName = "android")
        assertEquals(42, status.toAndroidGattStatus())
    }

    // =========================================================================
    // CCCD UUID
    // =========================================================================

    @Test
    fun `CCCD UUID is correct 2902`() {
        val expected = com.atruedev.kmpble.scanner.uuidFrom("2902")
        assertEquals(expected, AndroidGattServer.CCCD_UUID)
    }

    // =========================================================================
    // All properties flags
    // =========================================================================

    @Test
    fun `all properties set produces correct combined flags`() {
        val props = ServerCharacteristic.Properties(
            read = true,
            write = true,
            writeWithoutResponse = true,
            notify = true,
            indicate = true,
        )
        val flags = props.toAndroidProperties()
        assertTrue(flags and BluetoothGattCharacteristic.PROPERTY_READ != 0)
        assertTrue(flags and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
        assertTrue(flags and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
        assertTrue(flags and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
        assertTrue(flags and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
    }
}
