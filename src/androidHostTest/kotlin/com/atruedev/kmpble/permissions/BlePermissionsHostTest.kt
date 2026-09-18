package com.atruedev.kmpble.permissions

import android.Manifest
import com.atruedev.kmpble.KmpBle
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Validates [checkBlePermissions] against Robolectric's permission model.
 *
 * Instrumented tests cannot reliably revoke BLE permissions on API 33+ emulators
 * (they are auto-granted to the test APK), so denied/granted paths are covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BlePermissionsHostTest {
    @Before
    fun setup() {
        val field = KmpBle::class.java.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(KmpBle, null)
        ObservationPersistence.context.value = null

        KmpBle.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun checkPermissions_returnsDenied_withRequiredPermissions() {
        val result = checkBlePermissions()
        val denied = assertIs<PermissionResult.Denied>(result)

        assertContains(denied.permissions, Manifest.permission.BLUETOOTH_SCAN)
        assertContains(denied.permissions, Manifest.permission.BLUETOOTH_CONNECT)
        assertEquals(2, denied.permissions.size)
    }

    @Test
    fun checkPermissions_returnsGranted_whenAllRequiredPermissionsGranted() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        assertIs<PermissionResult.Granted>(checkBlePermissions())
    }
}
