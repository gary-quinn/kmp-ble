package com.atruedev.kmpble.gatt.internal

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
    data object DescriptorRead : PendingOp<GattResult>
    data object DescriptorWrite : PendingOp<GattStatus>
    data object RssiRead : PendingOp<Int>
    data object MtuRequest : PendingOp<Int>
}

/**
 * Holds at most one [CompletableDeferred] per [PendingOp] type.
 *
 * Confined to the owning peripheral's serialized dispatcher
 * (`limitedParallelism(1)`) — no synchronization required.
 */
internal class PendingOperations {
    private val slots = mutableMapOf<PendingOp<*>, CompletableDeferred<*>>()

    fun <T> set(op: PendingOp<T>, deferred: CompletableDeferred<T>) {
        check(op !in slots) { "${op::class.simpleName} overwritten while pending" }
        slots[op] = deferred
    }

    fun has(op: PendingOp<*>): Boolean = op in slots

    fun clear(op: PendingOp<*>) {
        slots.remove(op)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> complete(op: PendingOp<T>, value: T) {
        (slots.remove(op) as? CompletableDeferred<T>)?.complete(value)
    }

    fun fail(op: PendingOp<*>, cause: Throwable) {
        slots.remove(op)?.completeExceptionally(cause)
    }

    fun cancelAll(cause: Throwable) {
        slots.values.forEach { it.completeExceptionally(cause) }
        slots.clear()
    }
}
