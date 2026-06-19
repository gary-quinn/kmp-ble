@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.peripheral.internal.awaitGatt

/**
 * Connection parameter and PHY operation implementations for
 * [AndroidPeripheral].
 *
 * Extracted during second-pass decomposition (380 -> ~260) to keep the facade
 * under 300 lines.
 */

@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.requestConnectionPriorityGatt(priority: ConnectionPriority): Boolean {
    checkNotClosed()
    val androidPriority =
        when (priority) {
            ConnectionPriority.Balanced -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
            ConnectionPriority.High -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
            ConnectionPriority.LowPower -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
        }
    return peripheralContext.gattQueue.enqueue {
        bridge.requestConnectionPriority(androidPriority)
    }
}

@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.requestConnectionParameterUpdateGatt(
    params: ConnectionParameters,
): ConnectionParameterUpdateResult? {
    checkNotClosed()
    val androidPriority = params.intervalRange.toAndroidConnectionPriority()
    return peripheralContext.gattQueue.enqueue {
        val dispatched = bridge.requestConnectionPriority(androidPriority)
        if (!dispatched) return@enqueue null
        ConnectionParameterUpdateResult(
            negotiatedInterval = params.intervalRange.endInclusive,
            negotiatedLatency = params.slaveLatency,
            negotiatedSupervisionTimeout = params.supervisionTimeout,
        )
    }
}

@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.setPreferredPhyGatt(
    tx: Phy,
    rx: Phy,
): PhyResult? {
    checkNotClosed()
    val txMask = phyToMask(tx)
    val rxMask = phyToMask(rx)
    return peripheralContext.gattQueue.enqueue {
        val result =
            pendingOps.awaitGatt(PendingOp.PhyUpdate, "setPreferredPhy") {
                bridge.setPreferredPhy(
                    txMask,
                    rxMask,
                    BluetoothDevice.PHY_OPTION_NO_PREFERRED,
                )
            }
        if (!result.status.isSuccess()) return@enqueue null
        PhyResult(
            tx = phyConstantToPhy(result.txPhyConstant),
            rx = phyConstantToPhy(result.rxPhyConstant),
        )
    }
}

@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.readPhyGatt(): PhyResult? {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue {
        val result =
            pendingOps.awaitGatt(PendingOp.PhyRead, "readPhy") {
                bridge.readPhy()
            }
        if (!result.status.isSuccess()) return@enqueue null
        PhyResult(
            tx = phyConstantToPhy(result.txPhyConstant),
            rx = phyConstantToPhy(result.rxPhyConstant),
        )
    }
}
