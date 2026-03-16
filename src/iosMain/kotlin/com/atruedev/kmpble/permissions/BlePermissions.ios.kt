package com.atruedev.kmpble.permissions

import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted

/**
 * iOS BLE permission check.
 *
 * Checks `CBCentralManager.authorization` (iOS 13+).
 * The app must include `NSBluetoothAlwaysUsageDescription` in Info.plist.
 *
 * Note: this only checks authorization status — it does NOT trigger the
 * permission prompt. The prompt appears when a CBCentralManager is first created.
 */
public actual fun checkBlePermissions(): PermissionResult {
    val authorization = CBCentralManager.authorization
    return when (authorization) {
        CBManagerAuthorizationAllowedAlways -> PermissionResult.Granted
        CBManagerAuthorizationNotDetermined -> PermissionResult.Denied(listOf("bluetooth"))
        CBManagerAuthorizationDenied -> PermissionResult.PermanentlyDenied(listOf("bluetooth"))
        CBManagerAuthorizationRestricted -> PermissionResult.PermanentlyDenied(listOf("bluetooth"))
        else -> PermissionResult.Denied(listOf("bluetooth"))
    }
}
