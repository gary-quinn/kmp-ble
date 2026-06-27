package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.flow.StateFlow

/**
 * State management for a peripheral.
 *
 * Manages discovered services, MTU, and other state.
 * Delegates to [PeripheralContext] for reactive state exposure.
 */
internal class PeripheralState(
    val context: PeripheralContext,
) {
    /**
     * Currently discovered services.
     *
     * @return List of discovered services, or null if not discovered.
     */
    val services: StateFlow<List<DiscoveredService>?> get() = context.services

    /**
     * Current ATT MTU.
     *
     * @return Current MTU in bytes.
     */
    val mtu: StateFlow<Int> get() = context.mtu

    /**
     * Current maximum write value length.
     *
     * @return Maximum write value length in bytes.
     */
    val maximumWriteValueLength: StateFlow<Int> get() = context.maximumWriteValueLength
}
