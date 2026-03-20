package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerOptionRestoreIdentifierKey
import platform.darwin.dispatch_queue_create

internal object CentralManagerProvider {

    private val queue = dispatch_queue_create("com.atruedev.kmpble", null)
    private val delegate = KmpBleCentralDelegate()

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
                    delegate = delegate,
                    queue = queue,
                    options = mapOf<Any?, Any?>(
                        CBCentralManagerOptionRestoreIdentifierKey to restoreId,
                    ),
                )
            } else {
                CBCentralManager(
                    delegate = delegate,
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
