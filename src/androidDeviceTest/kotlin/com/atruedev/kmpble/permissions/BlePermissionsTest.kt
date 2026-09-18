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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Validates [checkBlePermissions] on a real Android runtime.
 *
 * Revokes BLE permissions first so the denied path is exercised even when the
 * emulator auto-grants them to the test APK.
 */
@RunWith(AndroidJUnit4::class)
class BlePermissionsTest {
    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext.applicationContext
        KmpBle.init(appContext)

        val uiAutomation = instrumentation.uiAutomation
        for (permission in listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) {
            uiAutomation.executeShellCommand("pm revoke ${appContext.packageName} $permission")
        }

        Assume.assumeFalse(
            "BLE permissions could not be revoked for denied-state test",
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    @Test
    fun checkPermissions_returnsDenied_withRequiredPermissions() {
        val result = checkBlePermissions()
        val denied = assertIs<PermissionResult.Denied>(result)

        assertContains(denied.permissions, Manifest.permission.BLUETOOTH_SCAN)
        assertContains(denied.permissions, Manifest.permission.BLUETOOTH_CONNECT)
        assertEquals(2, denied.permissions.size)
    }
}
