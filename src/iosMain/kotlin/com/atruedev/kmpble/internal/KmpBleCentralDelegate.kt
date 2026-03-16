package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOff
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCentralManagerStateResetting
import platform.CoreBluetooth.CBCentralManagerStateUnauthorized
import platform.CoreBluetooth.CBCentralManagerStateUnknown
import platform.CoreBluetooth.CBCentralManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import kotlin.concurrent.Volatile

/**
 * Raw scan result from CoreBluetooth delegate callback.
 */
internal data class RawScanResult(
    val peripheral: CBPeripheral,
    val advertisementData: Map<Any?, *>,
    val rssi: NSNumber,
)

/**
 * Unified CBCentralManager delegate handling both adapter state and scan results.
 */
internal class KmpBleCentralDelegate : NSObject(), CBCentralManagerDelegateProtocol {

    private val _adapterState = MutableStateFlow<BluetoothAdapterState>(BluetoothAdapterState.Unavailable)
    internal val adapterStateFlow: StateFlow<BluetoothAdapterState> = _adapterState.asStateFlow()

    private val _scanResults = MutableSharedFlow<RawScanResult>(extraBufferCapacity = 64)
    internal val scanResults: SharedFlow<RawScanResult> = _scanResults.asSharedFlow()

    // Copy-on-write map — reads from CoreBluetooth queue, writes from Kotlin coroutines.
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

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        _adapterState.value = when (central.state) {
            CBCentralManagerStatePoweredOn -> BluetoothAdapterState.On
            CBCentralManagerStatePoweredOff -> BluetoothAdapterState.Off
            CBCentralManagerStateResetting,
            CBCentralManagerStateUnknown -> BluetoothAdapterState.Unavailable
            CBCentralManagerStateUnauthorized -> BluetoothAdapterState.Unauthorized
            CBCentralManagerStateUnsupported -> BluetoothAdapterState.Unsupported
            else -> BluetoothAdapterState.Unavailable
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        _scanResults.tryEmit(
            RawScanResult(
                peripheral = didDiscoverPeripheral,
                advertisementData = advertisementData,
                rssi = RSSI,
            )
        )
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        val id = didConnectPeripheral.identifier.UUIDString
        connectionCallbacks[id]?.invoke(true, null)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        val id = didDisconnectPeripheral.identifier.UUIDString
        connectionCallbacks[id]?.invoke(false, error)
    }

    // K/N limitation: didFailToConnectPeripheral shares Kotlin type signature with
    // didDisconnectPeripheral — only one override possible. Connection failures
    // fall through to the connect() timeout. Wire via ObjC helper in a future release.
}
