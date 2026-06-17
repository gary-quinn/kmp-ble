package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError

/**
 * Interface for accessing delegate functionality without Kotlin/ObjC mixing issues.
 * Implemented by both delegate wrappers.
 */
internal interface CentralDelegate {
    val adapterStateFlow: StateFlow<BluetoothAdapterState>
    val scanResults: SharedFlow<RawScanResult>
    val restoredPeripherals: SharedFlow<List<CBPeripheral>>
    val isRestorationEnabled: Boolean

    fun registerConnectionCallback(
        peripheralId: String,
        callback: (connected: Boolean, error: NSError?) -> Unit,
    )

    fun unregisterConnectionCallback(peripheralId: String)

    fun handleConnectionFailure(
        peripheralId: String,
        error: NSError?,
    )
}
