package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.error.GattStatus
import kotlinx.atomicfu.AtomicLong
import kotlinx.atomicfu.atomic
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

    companion object {
        val all: List<PendingOp<*>> =
            listOf(
                CharacteristicRead,
                CharacteristicWrite,
                ReliableWriteCompleted,
                DescriptorRead,
                DescriptorWrite,
                RssiRead,
                MtuRequest,
                PhyUpdate,
                PhyRead,
                SubrateRequest,
            )
    }
}

/**
 * Armed-generation stamp for every op type, captured at event receipt (on the
 * platform callback thread) before the event is dispatched onto the peripheral's
 * serialized dispatcher.
 */
internal typealias GenerationSnapshot = Map<PendingOp<*>, Long?>

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
 * (`limitedParallelism(1)`) - no synchronization required. The one exception is
 * [generationOf]/[generationSnapshot], which read [armedGenerations] (an
 * atomicfu view kept in sync with the slots) from the platform callback thread
 * so callback events can be stamped before dispatch.
 *
 * ## Cancellation and staleness safety
 *
 * The GATT operation queue cancels in-flight actions when the caller's coroutine
 * is cancelled or times out (see [GattOperationQueue]). [awaitGatt] responds by
 * calling [cancel] with its generation token, which removes the slot. Without
 * this, a cancelled op would leave its slot armed forever -- the next operation
 * of the same type would then crash in [set] with
 * "overwritten while pending".
 *
 * [complete] and [fail] are generation-aware in the same way as [cancel]: the
 * callback handlers stamp each event with the generation armed when the event
 * was RECEIVED ([generationSnapshot], before the event is dispatched) and pass
 * it back here. A callback for a cancelled op therefore no-ops even when its
 * dispatch onto the serialized dispatcher runs after a retry already re-armed
 * the slot -- the stale callback can no longer complete the retry's deferred.
 * Residual window: if the retry's `set` executes before the platform even
 * delivers the stale response, the snapshot carries the retry's generation and
 * the response is indistinguishable from the retry's own; the platform
 * serializes responses in submission order, so this is the next response the
 * retry would have consumed anyway.
 */
internal class PendingOperations {
    private class Slot(
        val deferred: CompletableDeferred<*>,
        val generation: Long,
    )

    private val slots = mutableMapOf<PendingOp<*>, Slot>()
    private var nextGeneration = 0L

    /**
     * Thread-safe mirror of the armed generation per op type, readable from the
     * platform callback thread to stamp events before dispatch. [NO_SLOT] when
     * no slot is armed. Written only by the dispatcher-confined mutations below.
     */
    private val armedGenerations: Map<PendingOp<*>, AtomicLong> =
        PendingOp.all.associateWith { atomic(NO_SLOT) }

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
        armedGenerations.getValue(op).value = generation
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
        armedGenerations.getValue(op).value = NO_SLOT
    }

    fun has(op: PendingOp<*>): Boolean = op in slots

    /** Armed generation for [op], or null when no slot is armed. */
    fun generationOf(op: PendingOp<*>): Long? = armedGenerations.getValue(op).value.takeIf { it != NO_SLOT }

    /**
     * Armed generation for every op type at the moment of the call. Invoked on
     * the platform callback thread before the event is dispatched onto the
     * serialized dispatcher: the stamped generation is what the callback
     * belongs to, so a slot re-armed later by a retry cannot be completed by a
     * stale callback.
     */
    fun generationSnapshot(): GenerationSnapshot = PendingOp.all.associateWith { generationOf(it) }

    /**
     * Complete the slot for [op] only if it still holds [generation] -- the
     * generation stamped onto the event at receipt time. Null when no slot was
     * armed at receipt: the callback is stale and must no-op even if a retry
     * re-armed the slot before this ran.
     */
    fun <T> complete(
        op: PendingOp<T>,
        generation: Long?,
        value: T,
    ) {
        val slot = slots[op] ?: return
        if (generation == null || slot.generation != generation) return
        slots.remove(op)
        armedGenerations.getValue(op).value = NO_SLOT
        @Suppress("UNCHECKED_CAST")
        (slot.deferred as? CompletableDeferred<T>)?.complete(value)
    }

    /** Generation-guarded failure; same staleness semantics as [complete]. */
    fun fail(
        op: PendingOp<*>,
        generation: Long?,
        cause: Throwable,
    ) {
        val slot = slots[op] ?: return
        if (generation == null || slot.generation != generation) return
        slots.remove(op)
        armedGenerations.getValue(op).value = NO_SLOT
        @Suppress("UNCHECKED_CAST")
        (slot.deferred as? CompletableDeferred<Any?>)?.completeExceptionally(cause)
    }

    fun cancelAll(cause: Throwable) {
        slots.values.forEach { slot ->
            @Suppress("UNCHECKED_CAST")
            (slot.deferred as? CompletableDeferred<Any?>)?.completeExceptionally(cause)
        }
        slots.clear()
        armedGenerations.values.forEach { it.value = NO_SLOT }
    }

    private companion object {
        const val NO_SLOT = -1L
    }
}
