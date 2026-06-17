package com.atruedev.kmpble.internal

import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerRestoredStatePeripheralsKey
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject

/**
 * NSObject delegate WITHOUT state restoration support.
 * Forwards all delegate callbacks to a Kotlin CentralDelegate implementation.
 */
internal class KmpBleCentralDelegateObjC(
    private val delegate: CentralDelegate,
) : NSObject(),
    CBCentralManagerDelegateProtocol {
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        (delegate as? CentralDelegateImpl)?.handleAdapterStateUpdate(central)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        (delegate as? CentralDelegateImpl)?.handleScanResult(didDiscoverPeripheral, advertisementData, RSSI)
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral,
    ) {
        (delegate as? CentralDelegateImpl)?.handleConnect(didConnectPeripheral)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        (delegate as? CentralDelegateImpl)?.handleDisconnect(didDisconnectPeripheral, error)
    }
}

/**
 * NSObject delegate WITH state restoration support.
 * Forwards all delegate callbacks to a Kotlin CentralDelegate implementation.
 */
internal class KmpBleCentralDelegateWithRestorationObjC(
    private val delegate: CentralDelegate,
) : NSObject(),
    CBCentralManagerDelegateProtocol {
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        (delegate as? CentralDelegateImpl)?.handleAdapterStateUpdate(central)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        (delegate as? CentralDelegateImpl)?.handleScanResult(didDiscoverPeripheral, advertisementData, RSSI)
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral,
    ) {
        (delegate as? CentralDelegateImpl)?.handleConnect(didConnectPeripheral)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        (delegate as? CentralDelegateImpl)?.handleDisconnect(didDisconnectPeripheral, error)
    }

    override fun centralManager(
        central: CBCentralManager,
        willRestoreState: Map<Any?, *>,
    ) {
        (delegate as? CentralDelegateImpl)?.handleRestoredPeripherals(
            (willRestoreState[CBCentralManagerRestoredStatePeripheralsKey] as? List<*>)
                ?.filterIsInstance<CBPeripheral>()
                .orEmpty(),
        )
    }
}
