package com.atruedev.kmpble.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atruedev.kmpble.KmpBle
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Validates [checkBlePermissions] on a real Android runtime.
 *
 * On an emulator without granted BLE permissions, the result should be [PermissionResult.Denied]
 * with the correct permission strings. Tests are skipped if permissions are already granted
 * (e.g. via a test runner that auto-grants).
 */
@RunWith(AndroidJUnit4::class)
class BlePermissionsTest {
    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        KmpBle.init(appContext)

        Assume.assumeFalse(
            "BLE permissions already granted — skipping denial tests",
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    @Test
    fun checkPermissions_returnsDenied_whenPermissionsNotGranted() {
        val result = checkBlePermissions()
        assertIs<PermissionResult.Denied>(result)
    }

    @Test
    fun deniedResult_containsBluetoothScan() {
        val result = checkBlePermissions()
        assertIs<PermissionResult.Denied>(result)
        assertTrue(result.permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun deniedResult_containsBluetoothConnect() {
        val result = checkBlePermissions()
        assertIs<PermissionResult.Denied>(result)
        assertTrue(result.permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    fun deniedResult_containsExactlyTwoPermissions() {
        val result = checkBlePermissions()
        assertIs<PermissionResult.Denied>(result)
        assertTrue(
            result.permissions.size == 2,
            "Expected exactly 2 denied permissions, got ${result.permissions.size}: ${result.permissions}",
        )
    }
}
