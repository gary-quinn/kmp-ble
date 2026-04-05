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
 * Skipped when permissions are already granted (e.g. via auto-granting test runner).
 */
@RunWith(AndroidJUnit4::class)
class BlePermissionsTest {
    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        KmpBle.init(appContext)

        Assume.assumeFalse(
            "BLE permissions already granted",
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
