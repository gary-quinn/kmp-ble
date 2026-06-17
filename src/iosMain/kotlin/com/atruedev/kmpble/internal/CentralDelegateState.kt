package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerStatePoweredOff
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCentralManagerStateResetting
import platform.CoreBluetooth.CBCentralManagerStateUnauthorized
import platform.CoreBluetooth.CBCentralManagerStateUnknown
import platform.CoreBluetooth.CBCentralManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import kotlin.concurrent.Volatile

/**
 * Shared state and logic for CBCentralManager delegates.
 * Extracted to avoid Kotlin/Native restriction on subclassing Objective-C classes.
 */
internal class CentralDelegateState {
    private val _adapterStateFlow = MutableStateFlow<BluetoothAdapterState>(BluetoothAdapterState.Unavailable)
    internal val adapterStateFlow: StateFlow<BluetoothAdapterState> = _adapterStateFlow.asStateFlow()

    private val _scanResults = MutableSharedFlow<RawScanResult>(extraBufferCapacity = 64)
    internal val scanResults: SharedFlow<RawScanResult> = _scanResults.asSharedFlow()

    /**
     * Emits restored CBPeripherals from iOS state restoration.
     * Replay = 1 ensures the restoration event is not lost if observers register late
     * (after willRestoreState fires but before the consumer collects).
     */
    internal val _restoredPeripherals = MutableSharedFlow<List<CBPeripheral>>(replay = 1)
    internal val restoredPeripherals: SharedFlow<List<CBPeripheral>> = _restoredPeripherals.asSharedFlow()

    // Copy-on-write map - reads from CoreBluetooth queue, writes from Kotlin coroutines.
    // @Volatile ensures visibility across threads. Mutation creates a new map instance.
    @Volatile
    private var connectionCallbacks = mapOf<String, (connected: Boolean, error: NSError?) -> Unit>()

    internal fun registerConnectionCallback(
        peripheralId: String,
        callback: (connected: Boolean, error: NSError?) -> Unit,
    ) {
        connectionCallbacks = connectionCallbacks + (peripheralId to callback)
    }

    internal fun unregisterConnectionCallback(peripheralId: String) {
        connectionCallbacks = connectionCallbacks - peripheralId
    }

    internal fun handleAdapterStateUpdate(central: CBCentralManager) {
        _adapterStateFlow.value =
            when (central.state) {
                CBCentralManagerStatePoweredOn -> BluetoothAdapterState.On
                CBCentralManagerStatePoweredOff -> BluetoothAdapterState.Off
                CBCentralManagerStateResetting,
                CBCentralManagerStateUnknown,
                -> BluetoothAdapterState.Unavailable
                CBCentralManagerStateUnauthorized -> BluetoothAdapterState.Unauthorized
                CBCentralManagerStateUnsupported -> BluetoothAdapterState.Unsupported
                else -> BluetoothAdapterState.Unavailable
            }
    }

    internal fun handleScanResult(
        cbPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        rssi: NSNumber,
    ) {
        _scanResults.tryEmit(
            RawScanResult(
                peripheral = cbPeripheral,
                advertisementData = advertisementData,
                rssi = rssi,
            ),
        )
    }

    internal fun handleConnect(cbPeripheral: CBPeripheral) {
        val id = cbPeripheral.identifier.UUIDString
        connectionCallbacks[id]?.invoke(true, null)
    }

    internal fun handleDisconnect(
        cbPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        val id = cbPeripheral.identifier.UUIDString
        connectionCallbacks[id]?.invoke(false, error)
    }

    /**
     * Called by [KmpBleDelegateProxy] when didFailToConnectPeripheral fires.
     * Routes through the same connection callback as didDisconnectPeripheral.
     */
    internal fun handleConnectionFailure(
        peripheralId: String,
        error: platform.Foundation.NSError?,
    ) {
        connectionCallbacks[peripheralId]?.invoke(false, error)
    }

    internal fun handleRestoredPeripherals(peripherals: List<CBPeripheral>) {
        _restoredPeripherals.tryEmit(peripherals)
    }
}

/**
 * Raw scan result from CoreBluetooth delegate callback.
 */
internal data class RawScanResult(
    val peripheral: CBPeripheral,
    val advertisementData: Map<Any?, *>,
    val rssi: NSNumber,
)
