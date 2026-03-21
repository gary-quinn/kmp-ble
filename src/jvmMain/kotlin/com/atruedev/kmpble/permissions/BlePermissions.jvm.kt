package com.atruedev.kmpble.permissions

public actual fun checkBlePermissions(): PermissionResult =
    PermissionResult.Denied(listOf("BLE is not supported on JVM — BLE requires Android or iOS"))
