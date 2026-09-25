package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.permissions.PermissionResult

/**
 * Linux has no runtime Bluetooth permission prompt. Access means the process can reach
 * `org.bluez` on the system bus, which normally requires membership in the `bluetooth`
 * group or an equivalent polkit rule.
 */
internal object BlueZPermissions {
    const val DBUS_ACCESS = "org.bluez system D-Bus access (add the user to the 'bluetooth' group)"
    const val BLUETOOTHD = "bluetoothd (start it with: sudo systemctl start bluetooth)"

    fun check(): PermissionResult {
        if (!BlueZ.isLinux()) return PermissionResult.Denied(listOf("BlueZ requires Linux"))
        return try {
            BlueZ.openSystemBus().use { BlueZ.managedObjects(it) }
            PermissionResult.Granted
        } catch (e: Exception) {
            when {
                e.isAccessDenied() -> PermissionResult.PermanentlyDenied(listOf(DBUS_ACCESS))
                else -> PermissionResult.Denied(listOf(BLUETOOTHD))
            }
        }
    }
}
