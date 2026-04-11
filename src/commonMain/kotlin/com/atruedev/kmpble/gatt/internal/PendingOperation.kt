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

internal enum class PendingOp {
    CharacteristicRead,
    CharacteristicWrite,
    DescriptorRead,
    DescriptorWrite,
    RssiRead,
    MtuRequest,
}

internal class PendingOperations {
    private val slots = mutableMapOf<PendingOp, CompletableDeferred<*>>()

    fun <T> set(
        op: PendingOp,
        deferred: CompletableDeferred<T>,
    ) {
        check(op !in slots) { "${op.name} overwritten while pending" }
        slots[op] = deferred
    }

    fun has(op: PendingOp): Boolean = op in slots

    @Suppress("UNCHECKED_CAST")
    fun <T> complete(
        op: PendingOp,
        value: T,
    ) {
        (slots.remove(op) as? CompletableDeferred<T>)?.complete(value)
    }

    fun fail(
        op: PendingOp,
        cause: Throwable,
    ) {
        slots.remove(op)?.completeExceptionally(cause)
    }

    fun clear(op: PendingOp) {
        slots.remove(op)
    }

    fun cancelAll(cause: Throwable) {
        for (deferred in slots.values) {
            deferred.completeExceptionally(cause)
        }
        slots.clear()
    }
}
