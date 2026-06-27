package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * Service discovery and characteristic/descriptor lookup operations.
 *
 * Covers service discovery lifecycle: initial discovery, refresh, and stale state
 * detection. Also provides lookup helpers for finding characteristics and descriptors
 * by UUID from discovered services.
 *
 * The returned [Characteristic] and [Descriptor] instances are scoped to this
 * peripheral and must not be reused across peripherals - they encode the peripheral's
 * [PeripheralConnection.identifier] for thread-safety.
 *
 * @see PeripheralConnection for lifecycle management
 * @see PeripheralGatt for GATT read/write operations
 * @see PeripheralInfo for connection quality and MTU negotiation
 */
public interface PeripheralDiscovery {
    /**
     * Reactive state of discovered GATT services.
     *
     * Starts as `null` until the first [refreshServices] completes.
     * Updated after each successful discovery. Never `null` after
     * [PeripheralConnection.connect] completes (since connect triggers
     * service discovery automatically).
     *
     * Empty list means no services discovered - the peripheral may
     * be a GATT server only or the connection may be in progress.
     */
    public val services: StateFlow<List<DiscoveredService>?>

    /**
     * Discover all GATT services on this peripheral.
     *
     * Performs a full service discovery - clears the previous cache and
     * queries the remote device for all services. Returns the discovered
     * services on success.
     *
     * The returned list may be partial if the device advertises many
     * services - the client should handle incomplete discovery gracefully.
     *
     * @throws com.atruedev.kmpble.error.BleException if discovery fails
     * @throws com.atruedev.kmpble.error.BleException if discovery times out
     * @return List of discovered services
     */
    public suspend fun refreshServices(): List<DiscoveredService>

    /**
     * Find a characteristic by service and characteristic UUID.
     *
     * Returns `null` if no matching characteristic is found in [services].
     * Safe to call before [refreshServices] - returns `null` if services
     * have not yet been discovered.
     *
     * The returned [Characteristic] instance encodes the peripheral's
     * identifier for thread-safety - use the same instance for reads/writes.
     *
     * @param serviceUuid The service UUID to search
     * @param characteristicUuid The characteristic UUID to find
     * @return The matching [Characteristic], or `null` if not found
     */
    public fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic?

    /**
     * Find a descriptor by service, characteristic, and descriptor UUID.
     *
     * Returns `null` if no matching descriptor is found in [services].
     * Safe to call before [refreshServices] - returns `null` if services
     * have not yet been discovered.
     *
     * The returned [Descriptor] instance encodes the peripheral's
     * identifier for thread-safety - use the same instance for reads/writes.
     *
     * @param serviceUuid The service UUID containing the descriptor
     * @param characteristicUuid The characteristic containing the descriptor
     * @param descriptorUuid The descriptor UUID to find
     * @return The matching [Descriptor], or `null` if not found
     */
    public fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor?
}
