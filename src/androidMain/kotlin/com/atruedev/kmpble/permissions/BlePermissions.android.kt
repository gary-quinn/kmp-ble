package com.atruedev.kmpble.permissions

import android.Manifest
import android.content.pm.PackageManager
import com.atruedev.kmpble.KmpBle

/**
 * Android BLE permission check.
 *
 * On API 33+ (minSdk), the required permissions are:
 * - `BLUETOOTH_SCAN` — for scanning
 * - `BLUETOOTH_CONNECT` — for connecting, reading, writing
 *
 * `ACCESS_FINE_LOCATION` is NOT required on API 31+ unless scanning
 * with physical location intent (`ScanSettings.SCAN_TYPE_CLASSIC`).
 */
public actual fun checkBlePermissions(): PermissionResult {
    val context = KmpBle.requireContext()
    val required =
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

    val denied =
        required.filter { permission ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }

    return if (denied.isEmpty()) {
        PermissionResult.Granted
    } else {
        PermissionResult.Denied(denied)
    }
}
