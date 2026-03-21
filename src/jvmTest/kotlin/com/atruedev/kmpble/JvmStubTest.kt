package com.atruedev.kmpble

import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.permissions.checkBlePermissions
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.GattServer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JvmStubTest {

    @Test
    fun scannerThrowsOnJvm() {
        assertFailsWith<UnsupportedOperationException> { Scanner {} }
    }

    @Test
    fun gattServerThrowsOnJvm() {
        assertFailsWith<UnsupportedOperationException> { GattServer {} }
    }

    @Test
    fun advertiserThrowsOnJvm() {
        assertFailsWith<UnsupportedOperationException> { Advertiser() }
    }

    @Test
    fun bluetoothAdapterThrowsOnJvm() {
        assertFailsWith<UnsupportedOperationException> { BluetoothAdapter() }
    }

    @Test
    fun checkBlePermissionsReturnsDeniedOnJvm() {
        assertIs<PermissionResult.Denied>(checkBlePermissions())
    }

    @Test
    fun bleDataIsFunctionalOnJvm() {
        val data = BleData(byteArrayOf(1, 2, 3))
        kotlin.test.assertEquals(3, data.size)
        kotlin.test.assertEquals(1.toByte(), data[0])
        kotlin.test.assertEquals(byteArrayOf(2, 3).toList(), data.slice(1, 3).toByteArray().toList())
    }
}
