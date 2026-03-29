package com.atruedev.kmpble.sample.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

actual class PermissionRequester(
    private val activity: Activity,
) {
    private val blePermissions: Array<String> =
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    actual suspend fun requestBlePermissions(): PermissionResult {
        val deniedPermissions =
            blePermissions.filter { permission ->
                ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
            }

        if (deniedPermissions.isEmpty()) return PermissionResult.Granted

        val permanentlyDenied =
            deniedPermissions.filter { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
                    hasRequestedPermissionBefore(activity, permission)
            }

        if (permanentlyDenied.isNotEmpty()) {
            return PermissionResult.PermanentlyDenied(permanentlyDenied)
        }

        return PermissionResult.Denied(deniedPermissions)
    }

    private fun hasRequestedPermissionBefore(
        context: Context,
        permission: String,
    ): Boolean {
        val prefs = context.getSharedPreferences("ble_permissions", Context.MODE_PRIVATE)
        return prefs.getBoolean(permission, false)
    }

    fun markPermissionsRequested(permissions: List<String>) {
        val prefs = activity.getSharedPreferences("ble_permissions", Context.MODE_PRIVATE)
        prefs.edit().apply {
            permissions.forEach { putBoolean(it, true) }
            apply()
        }
    }
}
