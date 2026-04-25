package com.atruedev.kmpble.permissions

/**
 * Result of [com.atruedev.kmpble.permissions.checkBlePermissions].
 *
 * Check this before starting a scan or connection to provide appropriate UI guidance.
 */
public sealed interface PermissionResult {
    /** All required BLE permissions are granted - scanning and connection are allowed. */
    public data object Granted : PermissionResult

    /** One or more [permissions] were denied but can still be requested again. */
    public data class Denied(
        val permissions: List<String>,
    ) : PermissionResult

    /** One or more [permissions] were permanently denied - direct the user to system settings. */
    public data class PermanentlyDenied(
        val permissions: List<String>,
    ) : PermissionResult
}
