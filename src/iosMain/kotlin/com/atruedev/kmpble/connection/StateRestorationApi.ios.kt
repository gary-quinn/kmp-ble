package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.internal.StateRestorationHandler

/**
 * Enable iOS Core Bluetooth state restoration.
 *
 * Must be called before any BLE operations trigger CBCentralManager initialization
 * (before accessing Scanner, BluetoothAdapter, or connecting to peripherals).
 *
 * When enabled:
 * - CBCentralManager is created with [CBCentralManagerOptionRestoreIdentifierKey]
 * - Active observation subscriptions are persisted to the Keychain (encrypted)
 * - On app relaunch by iOS, previously connected peripherals are restored
 * - Observations are automatically re-subscribed
 *
 * Requirements:
 * - Add `bluetooth-central` to UIBackgroundModes in Info.plist
 * - Use a consistent [StateRestorationConfig.identifier] across app launches
 *
 * @throws IllegalStateException if CBCentralManager has already been initialized
 */
@ExperimentalBleApi
public actual fun enableStateRestoration(config: StateRestorationConfig) {
    require(config.identifier.isNotBlank()) {
        "State restoration identifier must not be blank"
    }

    CentralManagerProvider.restoreIdentifier = config.identifier
    StateRestorationHandler.start()
}
