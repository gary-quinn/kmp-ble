package com.atruedev.kmpble.internal

import platform.CoreBluetooth.CBPeripheralManager
import platform.darwin.dispatch_queue_create

/**
 * Provides the shared [CBPeripheralManager] instance.
 *
 * Separate from [CentralManagerProvider] — CBPeripheralManager and
 * CBCentralManager coexist independently. Each has its own dispatch
 * queue and delegate.
 */
internal object PeripheralManagerProvider {

    private val queue = dispatch_queue_create("com.atruedev.kmpble.server", null)

    internal val delegate = IosPeripheralManagerDelegate()

    /** Override for testing. Set before first access to [manager]. */
    internal var managerFactory: (() -> CBPeripheralManager)? = null

    internal val manager: CBPeripheralManager by lazy {
        managerFactory?.invoke() ?: CBPeripheralManager(
            delegate = delegate,
            queue = queue,
        )
    }
}
