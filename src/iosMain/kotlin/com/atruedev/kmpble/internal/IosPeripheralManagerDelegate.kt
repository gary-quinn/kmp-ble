package com.atruedev.kmpble.internal

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
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ObjCSignatureOverride

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
 */
internal class IosPeripheralManagerDelegate :
    NSObject(),
    CBPeripheralManagerDelegateProtocol {
    private val _managerState = MutableStateFlow<Long>(CBPeripheralManagerStateUnknown)
    internal val managerState: StateFlow<Long> = _managerState.asStateFlow()

    // Callbacks set by IosGattServer
    @Volatile
    internal var onServiceAdded: ((NSError?) -> Unit)? = null

    @Volatile
    internal var onReadRequest: ((CBPeripheralManager, CBATTRequest) -> Unit)? = null

    @Volatile
    internal var onWriteRequests: ((CBPeripheralManager, List<*>) -> Unit)? = null

    @Volatile
    internal var onSubscribe: ((CBCentral, CBCharacteristic) -> Unit)? = null

    @Volatile
    internal var onReadyToUpdate: (() -> Unit)? = null

    // Callback set by IosAdvertiser
    @Volatile
    internal var onStartAdvertising: ((NSError?) -> Unit)? = null

    // Callbacks set by IosL2capListener
    @Volatile
    internal var onPublishL2cap: ((CBL2CAPPSM, NSError?) -> Unit)? = null

    @Volatile
    internal var onOpenL2capChannel: ((CBL2CAPChannel?, NSError?) -> Unit)? = null

    @Volatile
    internal var onUnpublishL2cap: ((CBL2CAPPSM, NSError?) -> Unit)? = null

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
        onServiceAdded?.invoke(error)
    }

    override fun peripheralManagerDidStartAdvertising(
        peripheral: CBPeripheralManager,
        error: NSError?,
    ) {
        onStartAdvertising?.invoke(error)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didReceiveReadRequest: CBATTRequest,
    ) {
        onReadRequest?.invoke(peripheral, didReceiveReadRequest)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didReceiveWriteRequests: List<*>,
    ) {
        onWriteRequests?.invoke(peripheral, didReceiveWriteRequests)
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
        onSubscribe?.invoke(central, didSubscribeToCharacteristic)
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
        onReadyToUpdate?.invoke()
    }

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didPublishL2CAPChannel: CBL2CAPPSM,
        error: NSError?,
    ) {
        onPublishL2cap?.invoke(didPublishL2CAPChannel, error)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didOpenL2CAPChannel: CBL2CAPChannel?,
        error: NSError?,
    ) {
        onOpenL2capChannel?.invoke(didOpenL2CAPChannel, error)
    }

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didUnpublishL2CAPChannel: CBL2CAPPSM,
        error: NSError?,
    ) {
        onUnpublishL2cap?.invoke(didUnpublishL2CAPChannel, error)
    }
}
