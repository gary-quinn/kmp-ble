package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.error.GattStatus
import kotlinx.coroutines.CompletableDeferred

internal data class GattResult(
    val value: ByteArray,
    val status: GattStatus,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattResult) return false
        return value.contentEquals(other.value) && status == other.status
    }

    override fun hashCode(): Int = 31 * value.contentHashCode() + status.hashCode()
}

/**
 * Type-safe key for pending GATT operations. [T] binds each operation to its
 * result type so the compiler rejects mismatched completions.
 */
internal sealed interface PendingOp<T> {
    data object CharacteristicRead : PendingOp<GattResult>

    data object CharacteristicWrite : PendingOp<GattStatus>

    data object ReliableWriteCompleted : PendingOp<GattStatus>

    data object DescriptorRead : PendingOp<GattResult>

    data object DescriptorWrite : PendingOp<GattStatus>

    data object RssiRead : PendingOp<Int>

    data object MtuRequest : PendingOp<Int>

    data object PhyUpdate : PendingOp<PhyUpdateResult>

    data object PhyRead : PendingOp<PhyUpdateResult>

    data object SubrateRequest : PendingOp<ConnectionSubratingResult>
}

internal data class PhyUpdateResult(
    val txPhyConstant: Int,
    val rxPhyConstant: Int,
    val status: com.atruedev.kmpble.error.GattStatus,
)

/**
 * Holds at most one pending operation per [PendingOp] type, each tagged with a
 * generation token so a cancelled op cannot clobber a retry of the same type.
 *
 * Confined to the owning peripheral's serialized dispatcher
 * (`limitedParallelism(1)`) - no synchronization required.
 *
 * ## Cancellation safety
 *
 * The GATT operation queue cancels in-flight actions when the caller's coroutine
 * is cancelled or times out (see [GattOperationQueue]). [awaitGatt] responds by
 * calling [cancel] with its generation token, which removes the slot. Without
 * this, a cancelled op would leave its slot armed forever -- the next operation
 * of the same type would then crash in [set] with
 * "overwritten while pending", and a late platform callback would complete into
 * a freshly-armed retry slot.
 *
 * A callback that arrives after its op was cancelled no-ops UNLESS a retry of
 * the same type has already re-armed the slot: [complete] is not generation
 * aware, so a stale callback would complete the retry's deferred. In practice
 * the callback is dispatched from the platform callback thread onto the same
 * serialized dispatcher well before a user retry can re-arm (the retry path
 * crosses enqueue -> drain -> block -> set), so the race window is effectively
 * theoretical -- but it exists. Closing it fully requires generation-aware
 * [complete], tracked in the backlog.
 */
internal class PendingOperations {
    private class Slot(
        val deferred: CompletableDeferred<*>,
        val generation: Long,
    )

    private val slots = mutableMapOf<PendingOp<*>, Slot>()
    private var nextGeneration = 0L

    /**
     * Arm a pending slot. Returns the generation token the caller must pass to
     * [cancel] so only the current owner of the slot can clear it.
     */
    fun <T> set(
        op: PendingOp<T>,
        deferred: CompletableDeferred<T>,
    ): Long {
        check(op !in slots) { "${op::class.simpleName} overwritten while pending" }
        val generation = nextGeneration++
        slots[op] = Slot(deferred, generation)
        return generation
    }

    /**
     * Remove the slot for [op] if (and only if) it still belongs to [generation].
     * A cancelled op clears its own slot; a retry of the same type can then arm
     * a fresh slot without tripping the overwrite check. No-op if the slot was
     * already completed (replaced) by a newer operation.
     */
    fun cancel(
        op: PendingOp<*>,
        generation: Long,
    ) {
        val slot = slots[op] ?: return
        if (slot.generation != generation) return
        slots.remove(op)
    }

    fun has(op: PendingOp<*>): Boolean = op in slots

    fun <T> complete(
        op: PendingOp<T>,
        value: T,
    ) {
        @Suppress("UNCHECKED_CAST")
        (slots.remove(op)?.deferred as? CompletableDeferred<T>)?.complete(value)
    }

    fun fail(
        op: PendingOp<*>,
        cause: Throwable,
    ) {
        @Suppress("UNCHECKED_CAST")
        (slots.remove(op)?.deferred as? CompletableDeferred<Any?>)?.completeExceptionally(cause)
    }

    fun cancelAll(cause: Throwable) {
        slots.values.forEach { slot ->
            @Suppress("UNCHECKED_CAST")
            (slot.deferred as? CompletableDeferred<Any?>)?.completeExceptionally(cause)
        }
        slots.clear()
    }
}
