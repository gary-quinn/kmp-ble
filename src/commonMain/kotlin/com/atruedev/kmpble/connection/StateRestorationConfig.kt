package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi

/**
 * Configuration for iOS Core Bluetooth state restoration.
 *
 * When enabled, iOS can restore BLE connections after the app is terminated by the system.
 * On cold launch, previously connected peripherals are restored and observations
 * are automatically re-subscribed.
 *
 * State restoration requires:
 * - A unique [identifier] that iOS uses to associate the restored state with your app
 * - The `bluetooth-central` background mode in your app's Info.plist
 * - The `UIBackgroundModes` key must include `bluetooth-central`
 *
 * On Android, this configuration is ignored — Android handles BLE background differently.
 *
 * Usage:
 * ```kotlin
 * // Call once before any BLE operations (e.g., in Application.onCreate / AppDelegate)
 * KmpBle.enableStateRestoration(
 *     StateRestorationConfig(identifier = "com.myapp.ble")
 * )
 * ```
 */
@ExperimentalBleApi
public data class StateRestorationConfig(
    /**
     * Unique string that iOS uses to identify this app's CBCentralManager for restoration.
     * Must be consistent across app launches. Use a reverse-DNS identifier
     * (e.g., "com.myapp.ble.central").
     */
    val identifier: String,
)
