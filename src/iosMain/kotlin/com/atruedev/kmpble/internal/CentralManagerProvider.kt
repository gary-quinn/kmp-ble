package com.atruedev.kmpble.internal

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.darwin.dispatch_queue_create

internal object CentralManagerProvider {

    private val queue = dispatch_queue_create("com.atruedev.kmpble", null)
    private val delegate = KmpBleCentralDelegate()

    /** Override for testing. Set before first access to [manager]. */
    internal var managerFactory: (() -> CBCentralManager)? = null

    internal val manager: CBCentralManager by lazy {
        managerFactory?.invoke() ?: CBCentralManager(
            delegate = delegate,
            queue = queue,
        )
    }

    internal val adapterStateFlow: StateFlow<BluetoothAdapterState>
        get() = delegate.adapterStateFlow

    internal val scanDelegate: KmpBleCentralDelegate
        get() = delegate
}
