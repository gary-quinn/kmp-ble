package com.atruedev.kmpble.peripheral

/**
 * Pure decision helpers for when CoreBluetooth commands may be issued safely.
 * The iOS bridge maps live CBCentralManager/CBPeripheral state into these inputs.
 */
internal object CoreBluetoothCommandPolicy {
    fun canIssueCentralCommand(centralPoweredOn: Boolean): Boolean = centralPoweredOn

    fun canIssuePeripheralCommand(
        centralPoweredOn: Boolean,
        peripheralConnected: Boolean,
    ): Boolean = centralPoweredOn && peripheralConnected

    /**
     * Disconnect/cancel should be a quiet no-op when the adapter is off or the
     * peripheral is already disconnected, to avoid CoreBluetooth API MISUSE logs.
     */
    fun shouldSkipDisconnect(
        centralPoweredOn: Boolean,
        peripheralDisconnected: Boolean,
    ): Boolean = !centralPoweredOn || peripheralDisconnected
}
