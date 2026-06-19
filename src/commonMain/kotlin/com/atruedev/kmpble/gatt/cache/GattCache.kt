package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.DiscoveredService

/**
 * Caches discovered GATT services per peripheral to skip service discovery
 * on subsequent reconnections.
 *
 * Every major BLE SDK supports GATT caching: Nordic SoftDevice, TI BLE5-Stack,
 * ST BlueNRG, and Apple CoreBluetooth all cache discovered services. This
 * interface provides a consistent API across platforms.
 *
 * ## Platform behavior
 *
 * - **Android**: In-memory LRU cache with configurable max size. `BluetoothGatt`
 *   always queries the device on `discoverServices()`, so caching is essential
 *   for fast reconnection.
 * - **iOS**: Transparent pass-through. CoreBluetooth automatically caches
 *   discovered services and returns them without a GATT round-trip. [get]
 *   always returns `null` (cache miss) so callers fall through to
 *   [Peripheral.services][com.atruedev.kmpble.peripheral.Peripheral.services].
 * - **JVM**: Stub that always returns `null`. No Bluetooth stack is available.
 *
 * ## Usage
 *
 * ```kotlin
 * val cache: GattCache = createGattCache(maxSize = 32)
 *
 * suspend fun discover(peripheral: Peripheral): List<DiscoveredService> {
 *     return cache.get(peripheral.identifier)
 *         ?: peripheral.refreshServices().also { services ->
 *             cache.put(peripheral.identifier, services)
 *         }
 * }
 *
 * // After disconnection for a known-broken device:
 * cache.invalidate(peripheral.identifier)
 * ```
 *
 * Thread-safe across coroutines on all platforms.
 *
 * Implementation uses [kotlinx.coroutines.sync.Mutex] for structured
 * concurrency on Android, instead of [synchronized].
 */
public interface GattCache {
    /**
     * Retrieve cached services for [identifier], or `null` if no entry exists.
     *
     * On iOS this always returns `null` -- CoreBluetooth handles caching
     * internally and [Peripheral.services] returns cached results directly.
     */
    public suspend fun get(identifier: Identifier): List<DiscoveredService>?

    /**
     * Store discovered [services] for [identifier].
     *
     * Replaces any existing entry. Call after [Peripheral.refreshServices]
     * completes so the cache stays current with the actual GATT database.
     */
    public suspend fun put(
        identifier: Identifier,
        services: List<DiscoveredService>,
    )

    /**
     * Remove the cache entry for [identifier].
     *
     * Call when the peripheral's GATT database changes (e.g., firmware update
     * added a service) or when service discovery fails with a stale handle
     * error, forcing a fresh discovery on next connect.
     */
    public suspend fun invalidate(identifier: Identifier)

    /**
     * Remove all cached entries.
     *
     * Useful when the app resets Bluetooth state, or when switching between
     * environments (development/production) where peripheral configurations differ.
     */
    public suspend fun clear()
}

/**
 * Create a platform-appropriate [GattCache].
 *
 * - **Android**: In-memory LRU cache evicting the least-recently-used entry
 *   when size exceeds [maxSize].
 * - **iOS**: Passthrough stub (CoreBluetooth handles caching natively).
 * - **JVM**: Stub that always returns `null`.
 *
 * @param maxSize Maximum number of cached peripherals (Android only).
 *   Defaults to 64. Ignored on iOS and JVM.
 */
@Suppress("FUNCTION_NAME")
public expect fun createGattCache(maxSize: Int = 64): GattCache
