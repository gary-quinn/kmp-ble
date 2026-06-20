package com.atruedev.kmpble.internal

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBL2CAPPSM
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerStateUnknown
import platform.CoreBluetooth.CBService
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Immutable snapshot of all delegate callback registrations for
 * [IosPeripheralManagerDelegate]. Consolidates 9 individual [kotlin.concurrent.Volatile]
 * fields into a single atomic reference for consistent state reads.
 */
internal data class IosPeripheralDelegateState(
    val onServiceAdded: ((NSError?) -> Unit)? = null,
    val onReadRequest: ((CBPeripheralManager, CBATTRequest) -> Unit)? = null,
    val onWriteRequests: ((CBPeripheralManager, List<*>) -> Unit)? = null,
    val onSubscribe: ((CBCentral, CBCharacteristic) -> Unit)? = null,
    val onReadyToUpdate: (() -> Unit)? = null,
    val onStartAdvertising: ((NSError?) -> Unit)? = null,
    val onPublishL2cap: ((CBL2CAPPSM, NSError?) -> Unit)? = null,
    val onOpenL2capChannel: ((CBL2CAPChannel?, NSError?) -> Unit)? = null,
)

/**
 * Unified [CBPeripheralManager] delegate handling server, advertising,
 * and subscription callbacks.
 *
 * Similar pattern to [KmpBleCentralDelegate] - a single delegate handles
 * all CBPeripheralManager callbacks and routes to registered handlers.
 *
 * The delegate is set at CBPeripheralManager creation time (in
 * [PeripheralManagerProvider]) and remains for the lifetime of the manager.
 * Server and advertiser register their callbacks as needed.
 *
 * Callback state is held in a single [IosPeripheralDelegateState] stored
 * via kotlinx-atomicfu, replacing 9 individual @Volatile fields.
 */
internal class IosPeripheralManagerDelegate :
    NSObject(),
    CBPeripheralManagerDelegateProtocol {
    private val _managerState = MutableStateFlow<Long>(CBPeripheralManagerStateUnknown)
    internal val managerState: StateFlow<Long> = _managerState.asStateFlow()

    private val _state = atomic(IosPeripheralDelegateState())

    // Callbacks set by IosGattServer

    internal var onServiceAdded: ((NSError?) -> Unit)?
        get() = _state.value.onServiceAdded
        set(value) {
            _state.update { it.copy(onServiceAdded = value) }
        }

    internal var onReadRequest: ((CBPeripheralManager, CBATTRequest) -> Unit)?
        get() = _state.value.onReadRequest
        set(value) {
            _state.update { it.copy(onReadRequest = value) }
        }

    internal var onWriteRequests: ((CBPeripheralManager, List<*>) -> Unit)?
        get() = _state.value.onWriteRequests
        set(value) {
            _state.update { it.copy(onWriteRequests = value) }
        }

    internal var onSubscribe: ((CBCentral, CBCharacteristic) -> Unit)?
        get() = _state.value.onSubscribe
        set(value) {
            _state.update { it.copy(onSubscribe = value) }
        }

    internal var onReadyToUpdate: (() -> Unit)?
        get() = _state.value.onReadyToUpdate
        set(value) {
            _state.update { it.copy(onReadyToUpdate = value) }
        }

    // Callback set by IosAdvertiser

    internal var onStartAdvertising: ((NSError?) -> Unit)?
        get() = _state.value.onStartAdvertising
        set(value) {
            _state.update { it.copy(onStartAdvertising = value) }
        }

    internal var onPublishL2cap: ((CBL2CAPPSM, NSError?) -> Unit)?
        get() = _state.value.onPublishL2cap
        set(value) {
            _state.update { it.copy(onPublishL2cap = value) }
        }

    internal var onOpenL2capChannel: ((CBL2CAPChannel?, NSError?) -> Unit)?
        get() = _state.value.onOpenL2capChannel
        set(value) {
            _state.update { it.copy(onOpenL2capChannel = value) }
        }

    // --- Required delegate method ---

    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        _managerState.value = peripheral.state
    }

    // --- Optional delegate methods ---

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didAddService: CBService,
        error: NSError?,
    ) {
        _state.value.onServiceAdded?.invoke(error)
    }

    override fun peripheralManagerDidStartAdvertising(
        peripheral: CBPeripheralManager,
        error: NSError?,
    ) {
        _state.value.onStartAdvertising?.invoke(error)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didReceiveReadRequest: CBATTRequest,
    ) {
        _state.value.onReadRequest?.invoke(peripheral, didReceiveReadRequest)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didReceiveWriteRequests: List<*>,
    ) {
        _state.value.onWriteRequests?.invoke(peripheral, didReceiveWriteRequests)
    }

    // K/N limitation: peripheralManager:central:didSubscribeToCharacteristic: and
    // peripheralManager:central:didUnsubscribeFromCharacteristic: share the same
    // Kotlin type signature (CBPeripheralManager, CBCentral, CBCharacteristic).
    // Only one can be overridden - we choose subscribe since it's essential for
    // connection tracking. Unsubscribe events are not received.
    // See IosGattServer documentation for implications.
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeToCharacteristic: CBCharacteristic,
    ) {
        _state.value.onSubscribe?.invoke(central, didSubscribeToCharacteristic)
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
        _state.value.onReadyToUpdate?.invoke()
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didPublishL2CAPChannel: CBL2CAPPSM,
        error: NSError?,
    ) {
        _state.value.onPublishL2cap?.invoke(didPublishL2CAPChannel, error)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didOpenL2CAPChannel: CBL2CAPChannel?,
        error: NSError?,
    ) {
        _state.value.onOpenL2capChannel?.invoke(didOpenL2CAPChannel, error)
    }
}
