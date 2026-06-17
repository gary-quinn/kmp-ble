package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError
import platform.Foundation.NSNumber

/**
 * Kotlin implementation of CentralDelegate interface.
 * This can be wrapped by NSObject delegates for CoreBluetooth.
 */
internal class CentralDelegateImpl(
    private val state: CentralDelegateState = CentralDelegateState(),
    override val isRestorationEnabled: Boolean = false,
) : CentralDelegate {
    override val adapterStateFlow: StateFlow<BluetoothAdapterState> = state.adapterStateFlow
    override val scanResults: SharedFlow<RawScanResult> = state.scanResults
    override val restoredPeripherals: SharedFlow<List<CBPeripheral>> = state.restoredPeripherals

    override fun registerConnectionCallback(
        peripheralId: String,
        callback: (connected: Boolean, error: NSError?) -> Unit,
    ) = state.registerConnectionCallback(peripheralId, callback)

    override fun unregisterConnectionCallback(peripheralId: String) = state.unregisterConnectionCallback(peripheralId)

    override fun handleConnectionFailure(
        peripheralId: String,
        error: platform.Foundation.NSError?,
    ) = state.handleConnectionFailure(peripheralId, error)

    internal fun handleAdapterStateUpdate(central: CBCentralManager) {
        state.handleAdapterStateUpdate(central)
    }

    internal fun handleScanResult(
        cbPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        rssi: NSNumber,
    ) {
        state.handleScanResult(cbPeripheral, advertisementData, rssi)
    }

    internal fun handleConnect(cbPeripheral: CBPeripheral) {
        state.handleConnect(cbPeripheral)
    }

    internal fun handleDisconnect(
        cbPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        state.handleDisconnect(cbPeripheral, error)
    }

    internal fun handleRestoredPeripherals(peripherals: List<CBPeripheral>) {
        state.handleRestoredPeripherals(peripherals)
    }
}
