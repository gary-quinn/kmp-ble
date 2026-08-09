package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothGattCharacteristic
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import kotlinx.coroutines.CancellationException

/**
 * Reliable (prepared) write implementation for [AndroidPeripheral].
 *
 * Implements the all-or-nothing long-write contract of
 * [com.atruedev.kmpble.peripheral.Peripheral.writeReliable] using Android's
 * `BluetoothGatt` reliable-write API:
 *
 * ```
 * beginReliableWrite()              -- open transaction, stage subsequent writes
 * writeCharacteristic(chunk_1..n)  -- each staged (prepared), acked individually
 * executeReliableWrite()           -- commit everything
 * onReliableWriteCompleted(status) -- transaction result
 * ```
 *
 * On any failure the transaction is aborted via [abortReliableWrite], leaving
 * the peripheral untouched -- this is the atomicity `write()` (sequential
 * chunked) cannot provide.
 */

internal suspend fun AndroidPeripheral.writeReliableGatt(
    characteristic: Characteristic,
    data: ByteArray,
) {
    checkNotClosed()
    val native = requireNativeChar(characteristic)
    val maxLength = maximumWriteValueLength.value
    val chunks = LargeWriteHandler.chunk(data, maxLength)

    // Single chunk: no transaction needed, plain acknowledged write.
    if (chunks.size <= 1) {
        writeCharacteristicGatt(characteristic, data, WriteType.WithResponse)
        return
    }

    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        if (!bridge.beginReliableWrite()) {
            throw BleException(OperationFailed("beginReliableWrite initiation failed"))
        }
        try {
            for (chunk in chunks) {
                val status =
                    pendingOps.awaitGatt(PendingOp.CharacteristicWrite, "reliableWrite") {
                        bridge.writeCharacteristic(native, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    }
                if (!status.isSuccess()) {
                    throw BleException(GattError("reliableWrite", status))
                }
            }
            if (!bridge.executeReliableWrite()) {
                throw BleException(OperationFailed("executeReliableWrite initiation failed"))
            }
            val executeStatus =
                pendingOps.awaitGatt(PendingOp.ReliableWriteCompleted, "executeReliableWrite") { true }
            if (!executeStatus.isSuccess()) {
                throw BleException(GattError("reliableWrite", executeStatus))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            abortReliableWriteBestEffort()
            throw e
        }
    }
}

private fun AndroidPeripheral.abortReliableWriteBestEffort() {
    try {
        bridge.abortReliableWrite()
    } catch (_: Throwable) {
        // Best-effort: abort failures are non-fatal; the original error propagates.
    }
}
