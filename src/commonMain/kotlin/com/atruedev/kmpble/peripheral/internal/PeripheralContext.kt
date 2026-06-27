package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.PeripheralConnection
import com.atruedev.kmpble.peripheral.PeripheralEventHandler
import com.atruedev.kmpble.peripheral.PeripheralGATT
import com.atruedev.kmpble.peripheral.PeripheralState

/**
 * Per-peripheral internal context.
 *
 * Encapsulates shared state and dependencies for a single peripheral,
 * including:
 *  - Discovered GATT services and characteristics
 *  - Connection state
 *  - Bond state
 *  - RSSI value
 *  - Data length parameters
 *  - Connection options
 *  - GATT operation queue
 *  - Peripheral context
 */
internal class PeripheralContext(
    val identifier: Identifier,
    val state: StateFlow<ConnectionOptions.State> = MutableStateFlow(ConnectionOptions.State.Disconnected),
    val bondState: StateFlow<BondManager.BondState> = MutableStateFlow(BondManager.BondState.NotBonded),
    val services: StateFlow<List<DiscoveredService>> = MutableStateFlow(emptyList()),
    val dataLengthParameters: StateFlow<DataLengthParameters?> = MutableStateFlow(null),
    val rssi: StateFlow<Int?> = MutableStateFlow(null),
    val options: ConnectionOptions = ConnectionOptions(),
    val gattQueue: GattOperationQueue = GattOperationQueue(),
) {
    /**
     * Close the context and release resources.
     */
    fun close() {
        state.value = ConnectionOptions.State.Disconnected
    }
}
