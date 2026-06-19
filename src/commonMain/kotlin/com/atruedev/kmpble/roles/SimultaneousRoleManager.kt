package com.atruedev.kmpble.roles

import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScannerConfig
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.GattServerBuilder

/**
 * Manages simultaneous BLE central and peripheral roles on a single device.
 *
 * Wraps a [Scanner] (central role), an [Advertiser], and an optional [GattServer]
 * (peripheral role), providing shared lifecycle management. All three components
 * coexist without resource conflicts on both iOS and Android 10+.
 *
 * ## Usage
 *
 * ```kotlin
 * val roles = simultaneousRoles {
 *     scan {
 *         timeout = 30.seconds
 *         filters { match { serviceUuid("180d") } }
 *     }
 *     advertise(AdvertiseConfig(name = "MyDevice", connectable = true))
 *     server {
 *         service("180d") {
 *             characteristic("2a37") {
 *                 properties { read = true; notify = true }
 *                 permissions { read = true }
 *                 onRead { BleData(byteArrayOf(0x42)) }
 *             }
 *         }
 *     }
 * }
 *
 * // Use each role independently
 * roles.scanner.scanEvents.collect { event -> handleScanEvent(event) }
 * roles.advertiser.startAdvertising(AdvertiseConfig(name = "MyDevice"))
 * roles.gattServer?.open()
 *
 * // Clean up all roles with a single call
 * roles.close()
 * ```
 *
 * ## Platform support
 *
 * - iOS: [CBCentralManager] + [CBPeripheralManager] coexist natively.
 * - Android: [BluetoothLeScanner] + [BluetoothLeAdvertiser] share one
 *   [BluetoothAdapter]; simultaneous operation supported on Android 10+ (API 29).
 *
 * @see simultaneousRoles
 */
public class SimultaneousRoleManager(
    public val scanner: Scanner,
    public val advertiser: Advertiser,
    public val gattServer: GattServer? = null,
) : AutoCloseable {
    /**
     * Close all roles and release platform resources.
     *
     * Closes [scanner], [advertiser], and [gattServer] (if present).
     * Each close is best-effort — if one fails, the remaining are still closed.
     * Safe to call multiple times.
     */
    override fun close() {
        runCatching { scanner.close() }
        runCatching { advertiser.close() }
        runCatching { gattServer?.close() }
    }
}

/**
 * DSL builder for configuring simultaneous BLE central and peripheral roles.
 *
 * Provides a single entry point to create a [Scanner], [Advertiser], and
 * optional [GattServer] that coexist on the same device.
 */
public class SimultaneousRolesBuilder internal constructor() {
    internal var scanConfig: ScannerConfig.() -> Unit = {}
    internal var serverBlock: GattServerBuilder.() -> Unit = {}
    internal var advertiseConfig: AdvertiseConfig? = null

    /**
     * Configure the central-role scanner.
     *
     * @param block DSL block for [ScannerConfig].
     */
    public fun scan(block: ScannerConfig.() -> Unit) {
        scanConfig = block
    }

    /**
     * Configure the peripheral-role advertiser.
     *
     * @param config The advertising configuration.
     */
    public fun advertise(config: AdvertiseConfig) {
        advertiseConfig = config
    }

    /**
     * Configure the peripheral-role GATT server.
     *
     * @param block DSL block for [GattServerBuilder].
     */
    public fun server(block: GattServerBuilder.() -> Unit) {
        serverBlock = block
    }

    internal fun build(): SimultaneousRoleManager {
        val gattServer = GattServer(serverBlock)
        return SimultaneousRoleManager(
            scanner = Scanner(scanConfig),
            advertiser = Advertiser(),
            gattServer = gattServer,
        )
    }
}

/**
 * Create a [SimultaneousRoleManager] with DSL configuration.
 *
 * Configures the scanner, advertiser, and optional GATT server in one block.
 * All three roles coexist without resource conflicts.
 *
 * @param block DSL block for [SimultaneousRolesBuilder].
 * @return A [SimultaneousRoleManager] with all roles ready.
 */
public fun simultaneousRoles(
    block: SimultaneousRolesBuilder.() -> Unit,
): SimultaneousRoleManager =
    SimultaneousRolesBuilder().apply(block).build()
