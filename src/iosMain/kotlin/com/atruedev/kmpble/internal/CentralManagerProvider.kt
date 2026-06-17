@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.interop.KmpBleDelegateProxy
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionRestoreIdentifierKey
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

internal object CentralManagerProvider {
    private val queue = dispatch_queue_create("com.atruedev.kmpble", null)

    // Lazily initialized when manager is first accessed.
    // The delegate type depends on whether state restoration is enabled.
    private var _delegate: CentralDelegate? = null
    private var _objCDelegate: CBCentralManagerDelegateProtocol? = null
    private var _delegateProxy: KmpBleDelegateProxy? = null

    private val delegate: CentralDelegate
        get() {
            if (_delegate == null) {
                createDelegateAndProxy()
            }
            return _delegate!!
        }

    private val objCDelegate: CBCentralManagerDelegateProtocol
        get() {
            if (_objCDelegate == null) {
                createDelegateAndProxy()
            }
            return _objCDelegate!!
        }

    private val delegateProxy: KmpBleDelegateProxy
        get() {
            if (_delegateProxy == null) {
                createDelegateAndProxy()
            }
            return _delegateProxy!!
        }

    private fun createDelegateAndProxy() {
        val impl =
            if (restoreIdentifier != null) {
                CentralDelegateImpl(isRestorationEnabled = true)
            } else {
                CentralDelegateImpl()
            }
        _delegate = impl
        _objCDelegate =
            if (restoreIdentifier != null) {
                KmpBleCentralDelegateWithRestorationObjC(impl)
            } else {
                KmpBleCentralDelegateObjC(impl)
            }
        _delegateProxy =
            KmpBleDelegateProxy(target = objCDelegate).apply {
                onConnectionFailure = { peripheral, error ->
                    val id = peripheral?.identifier?.UUIDString
                    if (id != null) {
                        impl.handleConnectionFailure(id, error)
                    } else {
                        logEvent(
                            BleLogEvent.Error(
                                identifier = null,
                                message = "didFailToConnectPeripheral fired with null peripheral",
                                cause = null,
                            ),
                        )
                    }
                }
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
        // Access delegateProxy to trigger delegate creation with correct type
        val dp = delegateProxy
        managerFactory?.invoke() ?: run {
            val restoreId = restoreIdentifier
            if (restoreId != null) {
                CBCentralManager(
                    delegate = dp,
                    queue = queue,
                    options =
                        mapOf<Any?, Any?>(
                            CBCentralManagerOptionRestoreIdentifierKey to restoreId,
                        ),
                )
            } else {
                CBCentralManager(
                    delegate = dp,
                    queue = queue,
                )
            }
        }
    }

    internal val adapterStateFlow: StateFlow<BluetoothAdapterState>
        get() = delegate.adapterStateFlow

    internal val scanDelegate: CentralDelegate
        get() = delegate

    /** Whether state restoration is enabled. */
    internal val isStateRestorationEnabled: Boolean
        get() = restoreIdentifier != null
}
