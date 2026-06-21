package com.atruedev.kmpble.adapter

import com.atruedev.kmpble.internal.CentralManagerProvider
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.UIKit.UIDevice

/**
 * iOS Bluetooth adapter state observer.
 *
 * Accessing [state] lazily initializes the shared [CBCentralManager], which
 * triggers the iOS Bluetooth permission prompt on first access.
 */
public class IosBluetoothAdapter : BluetoothAdapter {
    override val state: StateFlow<BluetoothAdapterState>
        get() {
            CentralManagerProvider.manager
            return CentralManagerProvider.adapterStateFlow
        }

    /**
     * Best-effort capability detection.
     *
     * CoreBluetooth does not expose explicit PHY capability flags, so detection
     * is inferred from the iOS version string reported by [UIDevice.currentDevice].
     * BLE 5.0 hardware features (iPhone 8/X and newer) correspond to iOS 13+
     * CoreBluetooth support. Features not exposed by CoreBluetooth (LE Power
     * Control, Connection Subrating) report `false`.
     */
    override val capabilities: BleCapabilities by lazy {
        val versionString = UIDevice.currentDevice.systemVersion
        val major = versionString.split(".").firstOrNull()?.toIntOrNull() ?: 0

        // CoreBluetooth exposes BLE 5.0 features from iOS 13+
        val hasBle50 = major >= 13
        // LE Audio support introduced in iOS 15+
        val hasLeAudio = major >= 15

        BleCapabilities(
            supportsExtendedAdvertising = hasBle50,
            supportsLe2mPhy = hasBle50,
            supportsLeCodedPhy = hasBle50,
            supportsPeriodicAdvertising = hasBle50,
            supportsLePowerControl = false,
            supportsLeAudio = hasLeAudio,
            supportsConnectionSubrating = false,
        )
    }

    override fun close() {
        // Delegates to singleton - no per-instance cleanup needed
    }
}
