package com.atruedev.kmpble.sample.permission

import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted

actual class PermissionRequester {
    actual suspend fun requestBlePermissions(): PermissionResult =
        when (CBCentralManager.authorization) {
            CBManagerAuthorizationAllowedAlways -> PermissionResult.Granted
            CBManagerAuthorizationNotDetermined -> PermissionResult.Denied(listOf("bluetooth"))
            CBManagerAuthorizationDenied -> PermissionResult.PermanentlyDenied(listOf("bluetooth"))
            CBManagerAuthorizationRestricted -> PermissionResult.PermanentlyDenied(listOf("bluetooth"))
            else -> PermissionResult.Denied(listOf("bluetooth"))
        }
}
