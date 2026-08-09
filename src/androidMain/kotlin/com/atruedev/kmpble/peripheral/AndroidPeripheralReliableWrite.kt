package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothGattCharacteristic
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.peripheral.internal.awaitGatt

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
 * On ANY failure -- including cancellation of the calling coroutine -- the
 * transaction is aborted via [abortReliableWrite], leaving the peripheral
 * untouched. This is the atomicity `write()` (sequential chunked) cannot provide.
 *
 * The whole transaction runs under [com.atruedev.kmpble.connection.OperationTimeouts.reliableWrite]
 * (default 30s) -- a per-chunk timeout would starve slow links for exactly the
 * multi-chunk payloads this API exists for.
 *
 * ## Cancellation and aborts
 *
 * Caller cancellation propagates into the in-flight transaction: the operation
 * queue runs each action as a child job and cancels it when the caller gives up
 * (see [com.atruedev.kmpble.gatt.internal.GattOperationQueue]), so the block
 * observes [kotlinx.coroutines.CancellationException] and the [catch] below
 * aborts the transaction before rethrowing.
 *
 * The abort itself is best-effort: if it fails (device hung), the reliable
 * session stays open on the device and subsequent GATT operations may be staged
 * into it -- recover by disconnecting. This is the documented platform
 * limitation of `abortReliableWrite`, not a silent failure.
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

    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.reliableWrite) {
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
            // Arm the pending slot before submitting so a synchronous callback
            // still finds its deferred, but keep initiation failures typed as
            // OperationFailed -- they are not GATT status responses.
            val executeDeferred = kotlinx.coroutines.CompletableDeferred<GattStatus>()
            pendingOps.set(PendingOp.ReliableWriteCompleted, executeDeferred)
            val dispatched = bridge.executeReliableWrite()
            if (!dispatched) {
                pendingOps.clear(PendingOp.ReliableWriteCompleted)
                throw BleException(OperationFailed("executeReliableWrite initiation failed"))
            }
            val executeStatus = executeDeferred.await()
            if (!executeStatus.isSuccess()) {
                throw BleException(GattError("reliableWrite", executeStatus))
            }
        } catch (e: Throwable) {
            // Single catch: CancellationException is a Throwable, so a cancelled
            // transaction is aborted too, not silently left to commit later.
            abortReliableWriteBestEffort()
            throw e
        }
    }
}

/**
 * Best-effort abort. The original error always propagates; abort failures are
 * swallowed (a hung device cannot be aborted, and there is no recovery path
 * other than disconnect -- see the KDoc limitation note).
 */
private fun AndroidPeripheral.abortReliableWriteBestEffort() {
    try {
        bridge.abortReliableWrite()
    } catch (_: Throwable) {
        // Best-effort: abort failures are non-fatal; the original error propagates.
    }
}
