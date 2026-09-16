package com.atruedev.kmpble.internal

import com.atruedev.kmpble.peripheral.CoreBluetoothCommandPolicy
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralStateConnected
import platform.CoreBluetooth.CBPeripheralStateDisconnected

/**
 * Live CoreBluetooth state checks used by [com.atruedev.kmpble.peripheral.ApplePeripheralBridge]
 * before issuing native commands. Failing fast here prevents API MISUSE console warnings and
 * keeps typed Kotlin errors on the caller path instead of undefined native behavior.
 */
internal object CoreBluetoothGuards {
    fun isCentralPoweredOn(): Boolean =
        CentralManagerProvider.manager.state == CBCentralManagerStatePoweredOn

    fun isPeripheralConnected(peripheral: CBPeripheral): Boolean =
        peripheral.state == CBPeripheralStateConnected

    fun canIssueCentralCommand(): Boolean =
        CoreBluetoothCommandPolicy.canIssueCentralCommand(isCentralPoweredOn())

    fun canIssuePeripheralCommand(peripheral: CBPeripheral): Boolean =
        CoreBluetoothCommandPolicy.canIssuePeripheralCommand(
            centralPoweredOn = isCentralPoweredOn(),
            peripheralConnected = isPeripheralConnected(peripheral),
        )

    fun shouldSkipDisconnect(peripheral: CBPeripheral): Boolean =
        CoreBluetoothCommandPolicy.shouldSkipDisconnect(
            centralPoweredOn = isCentralPoweredOn(),
            peripheralDisconnected = peripheral.state == CBPeripheralStateDisconnected,
        )
}
