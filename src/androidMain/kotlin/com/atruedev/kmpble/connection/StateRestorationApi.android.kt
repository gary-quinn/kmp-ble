package com.atruedev.kmpble.connection


/**
 * Android no-op. Android handles BLE background via foreground services,
 * not Core Bluetooth state restoration.
 */
public actual fun enableStateRestoration(config: StateRestorationConfig) {
    // No-op on Android
}
