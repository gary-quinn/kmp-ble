package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.interop.KmpBleDelegateProxy
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerOptionRestoreIdentifierKey
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

internal object CentralManagerProvider {
    private val queue = dispatch_queue_create("com.atruedev.kmpble", null)
    private val delegate = KmpBleCentralDelegate()

    // ObjC proxy handles didFailToConnectPeripheral (K/N can't override it due to
    // signature collision with didDisconnectPeripheral). All other delegate methods
    // forward to the Kotlin delegate via ObjC message forwarding.
    private val delegateProxy =
        KmpBleDelegateProxy(target = delegate).apply {
            onConnectionFailure = { peripheral, error ->
                val id = peripheral?.identifier?.UUIDString
                if (id == null) {
                    logEvent(
                        BleLogEvent.Error(
                            identifier = null,
                            message = "didFailToConnectPeripheral fired with null peripheral",
                            cause = null,
                        ),
                    )
                    return@apply
                }
                delegate.handleConnectionFailure(id, error)
            }
        }

    /**
     * State restoration identifier. Set via [enableStateRestoration] before
     * first access to [manager]. When non-null, the CBCentralManager is created
     * with CBCentralManagerOptionRestoreIdentifierKey, enabling iOS to restore
     * BLE connections after app termination.
     */
    @Volatile
    internal var restoreIdentifier: String? = null

    /** Override for testing. Set before first access to [manager]. */
    internal var managerFactory: (() -> CBCentralManager)? = null

    internal val manager: CBCentralManager by lazy {
        managerFactory?.invoke() ?: run {
            val restoreId = restoreIdentifier
            if (restoreId != null) {
                CBCentralManager(
                    delegate = delegateProxy,
                    queue = queue,
                    options =
                        mapOf<Any?, Any?>(
                            CBCentralManagerOptionRestoreIdentifierKey to restoreId,
                        ),
                )
            } else {
                CBCentralManager(
                    delegate = delegateProxy,
                    queue = queue,
                )
            }
        }
    }

    internal val adapterStateFlow: StateFlow<BluetoothAdapterState>
        get() = delegate.adapterStateFlow

    internal val scanDelegate: KmpBleCentralDelegate
        get() = delegate

    /** Whether state restoration is enabled. */
    internal val isStateRestorationEnabled: Boolean
        get() = restoreIdentifier != null
}
