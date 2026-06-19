package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy

@ExperimentalBleApi
internal suspend fun IosPeripheral.requestConnectionPriorityExt(priority: ConnectionPriority): Boolean {
    checkNotClosed()
    return false
}

@ExperimentalBleApi
internal suspend fun IosPeripheral.requestConnectionParameterUpdateExt(
    params: ConnectionParameters,
): ConnectionParameterUpdateResult? {
    checkNotClosed()
    return null
}

@ExperimentalBleApi
internal suspend fun IosPeripheral.setPreferredPhyExt(
    tx: Phy,
    rx: Phy,
): PhyResult? {
    checkNotClosed()
    return null
}

@ExperimentalBleApi
internal suspend fun IosPeripheral.readPhyExt(): PhyResult? {
    checkNotClosed()
    return null
}
