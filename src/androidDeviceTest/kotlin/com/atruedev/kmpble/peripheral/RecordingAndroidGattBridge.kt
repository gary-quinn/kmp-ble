package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context

/**
 * Test [AndroidGattBridge] that records reliable-write calls and dispatches
 * GATT callbacks through [AndroidPeripheral.handleGattEvent] on the peripheral's
 * serialized dispatcher so [PendingOperations] completions race-free with awaits.
 */
internal class RecordingAndroidGattBridge(
    device: BluetoothDevice,
    context: Context,
) : AndroidGattBridge(device, context) {
    internal lateinit var peripheral: AndroidPeripheral

    val calls = mutableListOf<ReliableWriteCall>()

    var beginReliableWriteResult = true
    var executeReliableWriteResult = true
    var autoCompleteWrites = true
    var autoCompleteReliableWrite = true
    var writeCharacteristicStatus = BluetoothGatt.GATT_SUCCESS
    var reliableWriteCompletedStatus = BluetoothGatt.GATT_SUCCESS

    /** When true, only the first [writeCharacteristic] dispatches a write callback. */
    var autoCompleteFirstWriteOnly = false

    private var writeDispatchCount = 0

    override fun beginReliableWrite(): Boolean {
        calls += ReliableWriteCall.BeginReliableWrite
        return beginReliableWriteResult
    }

    override fun executeReliableWrite(): Boolean {
        calls += ReliableWriteCall.ExecuteReliableWrite
        val dispatched = executeReliableWriteResult
        if (dispatched && autoCompleteReliableWrite) {
            dispatchReliableWriteCompleted(reliableWriteCompletedStatus)
        }
        return dispatched
    }

    override fun abortReliableWrite() {
        calls += ReliableWriteCall.AbortReliableWrite
    }

    override fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        calls += ReliableWriteCall.WriteCharacteristic(value.size)
        writeDispatchCount++
        val shouldDispatch =
            when {
                !autoCompleteWrites -> false
                autoCompleteFirstWriteOnly -> writeDispatchCount == 1
                else -> true
            }
        if (shouldDispatch) {
            dispatchCharacteristicWrite(characteristic, writeCharacteristicStatus)
        }
        return true
    }

    fun resetRecording() {
        calls.clear()
        writeDispatchCount = 0
    }

    private fun dispatchCharacteristicWrite(
        characteristic: BluetoothGattCharacteristic,
        status: Int,
    ) {
        peripheral.handleGattEvent(GattCallbackEvent.CharacteristicWrite(characteristic, status))
    }

    private fun dispatchReliableWriteCompleted(status: Int) {
        peripheral.handleGattEvent(GattCallbackEvent.ReliableWriteCompleted(status))
    }
}

internal sealed class ReliableWriteCall {
    data object BeginReliableWrite : ReliableWriteCall()

    data object ExecuteReliableWrite : ReliableWriteCall()

    data object AbortReliableWrite : ReliableWriteCall()

    data class WriteCharacteristic(
        val payloadSize: Int,
    ) : ReliableWriteCall()
}
