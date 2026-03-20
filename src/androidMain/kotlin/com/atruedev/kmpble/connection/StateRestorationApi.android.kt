package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi

/**
 * Android no-op. Android handles BLE background via foreground services,
 * not Core Bluetooth state restoration.
 */
@ExperimentalBleApi
public actual fun enableStateRestoration(config: StateRestorationConfig) {
    // No-op on Android
}
