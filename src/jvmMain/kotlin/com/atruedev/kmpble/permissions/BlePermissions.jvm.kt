package com.atruedev.kmpble.permissions

public actual fun checkBlePermissions(): PermissionResult =
    PermissionResult.Denied(listOf("BLE is not available on JVM"))
