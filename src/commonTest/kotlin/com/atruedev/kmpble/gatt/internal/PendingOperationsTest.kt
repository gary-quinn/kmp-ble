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
        val secondGen = ops.set(PendingOp.CharacteristicWrite, second)
        ops.complete(PendingOp.CharacteristicWrite, secondGen, GattStatus.Success)
        assertTrue(second.isCompleted)
    }

    @Test
    fun lateCallbackAfterCancelNoOps() {
        val ops = PendingOperations()
        val deferred = CompletableDeferred<GattStatus>()
        val generation = ops.set(PendingOp.CharacteristicWrite, deferred)
        ops.cancel(PendingOp.CharacteristicWrite, generation)

        // A platform callback arriving after the op was cancelled carries no
        // armed generation (the handler stamps null) and must not complete the
        // (already abandoned) deferred.
        ops.complete(PendingOp.CharacteristicWrite, null, GattStatus.Success)
        assertFalse(deferred.isCompleted)
    }

    @Test
    fun staleCallbackAfterRetryReArmDoesNotCompleteRetry() {
        val ops = PendingOperations()
        val first = CompletableDeferred<GattStatus>()
        val firstGen = ops.set(PendingOp.CharacteristicWrite, first)
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)

        // Retry re-arms the slot before the stale callback's dispatch runs.
        val retry = CompletableDeferred<GattStatus>()
        val retryGen = ops.set(PendingOp.CharacteristicWrite, retry)

        // The stale callback was received while no slot was armed, so the
        // handler stamped null: it must not complete, let alone clear, the
        // retry's slot.
        ops.complete(PendingOp.CharacteristicWrite, null, GattStatus.Success)
        assertFalse(retry.isCompleted)
        assertTrue(ops.has(PendingOp.CharacteristicWrite))

        // The retry's own response still completes it.
        ops.complete(PendingOp.CharacteristicWrite, retryGen, GattStatus.Success)
        assertTrue(retry.isCompleted)
    }

    @Test
    fun staleGenerationCompleteDoesNotClearNewSlot() {
        val ops = PendingOperations()
        val first = CompletableDeferred<GattStatus>()
        val firstGen = ops.set(PendingOp.CharacteristicWrite, first)
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)

        val retry = CompletableDeferred<GattStatus>()
        ops.set(PendingOp.CharacteristicWrite, retry)

        // A completion carrying the OLD generation must not clear the new slot.
        ops.complete(PendingOp.CharacteristicWrite, firstGen, GattStatus.Success)
        assertFalse(retry.isCompleted)
        assertTrue(ops.has(PendingOp.CharacteristicWrite))
    }

    @Test
    fun staleGenerationFailDoesNotClearNewSlot() {
        val ops = PendingOperations()
        val first = CompletableDeferred<GattStatus>()
        val firstGen = ops.set(PendingOp.CharacteristicWrite, first)
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)

        val retry = CompletableDeferred<GattStatus>()
        val retryGen = ops.set(PendingOp.CharacteristicWrite, retry)

        // A failure carrying the OLD generation must not clear the new slot.
        ops.fail(PendingOp.CharacteristicWrite, firstGen, RuntimeException("stale"))
        assertFalse(retry.isCompleted)
        assertTrue(ops.has(PendingOp.CharacteristicWrite))

        ops.complete(PendingOp.CharacteristicWrite, retryGen, GattStatus.Success)
        assertTrue(retry.isCompleted)
    }

    @Test
    fun staleGenerationCancelDoesNotClearNewSlot() {
        val ops = PendingOperations()
        val first = CompletableDeferred<GattStatus>()
        val firstGen = ops.set(PendingOp.CharacteristicWrite, first)
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)

        val second = CompletableDeferred<GattStatus>()
        val secondGen = ops.set(PendingOp.CharacteristicWrite, second)

        // A stray cancel carrying the OLD generation must not clear the new slot.
        ops.cancel(PendingOp.CharacteristicWrite, firstGen)
        ops.complete(PendingOp.CharacteristicWrite, secondGen, GattStatus.Success)
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
