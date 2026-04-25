package com.atruedev.kmpble.permissions

/**
 * Check whether the required BLE permissions are granted.
 *
 * This does NOT request permissions - it only checks the current state.
 * Use platform-specific APIs (ActivityResultContracts on Android, Info.plist
 * on iOS) to request permissions before calling this.
 *
 * ```kotlin
 * when (val result = checkBlePermissions()) {
 *     is PermissionResult.Granted -> scanner.advertisements.collect { ... }
 *     is PermissionResult.Denied -> showPermissionRationale(result.permissions)
 *     is PermissionResult.PermanentlyDenied -> showSettingsPrompt(result.permissions)
 * }
 * ```
 */
public expect fun checkBlePermissions(): PermissionResult
