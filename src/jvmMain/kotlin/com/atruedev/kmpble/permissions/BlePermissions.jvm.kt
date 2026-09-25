@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.permissions

import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.KmpBleBackendApi

/**
 * JVM BLE permission check, delegated to the active backend: D-Bus access to `org.bluez`
 * on Linux, `CBManager.authorization` on macOS. [PermissionResult.Denied] when no backend
 * supports this host.
 */
public actual fun checkBlePermissions(): PermissionResult =
    BleBackends.current()?.checkPermissions()
        ?: PermissionResult.Denied(listOf(BleBackends.unavailableMessage("BLE")))
