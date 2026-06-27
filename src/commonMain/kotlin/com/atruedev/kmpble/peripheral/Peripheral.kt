package com.atruedev.kmpble.peripheral

/**
 * Unified peripheral interface combining connection, discovery, GATT, and info operations.
 *
 * This is the public API that all peripheral implementations (AndroidPeripheral,
 * IosPeripheral, FakePeripheral) implement. It extends the focused sub-interfaces
 * for organizational clarity:
 * - [PeripheralConnection] - lifecycle and bond management
 * - [PeripheralDiscovery] - service discovery and characteristic lookup
 * - [PeripheralGatt] - GATT read/write/observe operations
 * - [PeripheralInfo] - connection quality and transport-level operations
 *
 * Implementations should focus on the sub-interfaces to keep concerns separate.
 */
public interface Peripheral :
    PeripheralConnection,
    PeripheralDiscovery,
    PeripheralGatt,
    PeripheralInfo
