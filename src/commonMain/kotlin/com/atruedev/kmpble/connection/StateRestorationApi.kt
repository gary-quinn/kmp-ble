package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi

/**
 * Enable iOS Core Bluetooth state restoration.
 *
 * Call this once before any BLE operations (e.g., in your app's initialization).
 * On iOS, this configures the CBCentralManager with a restoration identifier so
 * iOS can restore BLE connections after app termination.
 *
 * On Android, this is a no-op — Android handles BLE background differently
 * (via foreground services).
 *
 * @param config Configuration specifying the restoration identifier.
 */
@ExperimentalBleApi
public expect fun enableStateRestoration(config: StateRestorationConfig)
