package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.error.GattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingOperationsTest {
    @Test
    fun setRejectsOverwriteWhilePending() {
        val ops = PendingOperations()
        ops.set(PendingOp.CharacteristicWrite, CompletableDeferred())
        assertFailsWith<IllegalStateException> {
            ops.set(PendingOp.CharacteristicWrite, CompletableDeferred())
        }
    }

    @Test
    fun retryAfterCancelDoesNotCrash() {
        val ops = PendingOperations()
        val first = CompletableDeferred<GattStatus>()
        val firstGen = ops.set(PendingOp.CharacteristicWrite, first)
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)

        // The cancellation-safe contract: a retry of the same op type must be
        // able to arm a fresh slot (this crashed before the generation token).
        val second = CompletableDeferred<GattStatus>()
        ops.set(PendingOp.CharacteristicWrite, second)
        ops.complete(PendingOp.CharacteristicWrite, GattStatus.Success)
        assertTrue(second.isCompleted)
    }

    @Test
    fun lateCallbackAfterCancelNoOps() {
        val ops = PendingOperations()
        val deferred = CompletableDeferred<GattStatus>()
        val generation = ops.set(PendingOp.CharacteristicWrite, deferred)
        ops.cancel(PendingOp.CharacteristicWrite, generation)

        // A platform callback arriving after the op was cancelled must not
        // complete the (already abandoned) deferred.
        ops.complete(PendingOp.CharacteristicWrite, GattStatus.Success)
        assertFalse(deferred.isCompleted)
    }

    @Test
    fun staleGenerationCancelDoesNotClearNewSlot() {
        val ops = PendingOperations()
        val first = CompletableDeferred<GattStatus>()
        val firstGen = ops.set(PendingOp.CharacteristicWrite, first)
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)

        val second = CompletableDeferred<GattStatus>()
        ops.set(PendingOp.CharacteristicWrite, second)

        // A stray cancel carrying the OLD generation must not clear the new slot.
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)
        ops.complete(PendingOp.CharacteristicWrite, GattStatus.Success)
        assertTrue(second.isCompleted)
    }

    @Test
    fun cancelAllFailsAllPending() {
        val ops = PendingOperations()
        val deferred = CompletableDeferred<GattStatus>()
        ops.set(PendingOp.CharacteristicWrite, deferred)

        ops.cancelAll(RuntimeException("disconnected"))

        assertTrue(deferred.isCompleted && deferred.getCompletionExceptionOrNull() != null)
    }
}
