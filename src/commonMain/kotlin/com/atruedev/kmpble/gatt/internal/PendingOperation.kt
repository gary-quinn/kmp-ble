package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.error.GattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlin.reflect.KProperty

internal data class GattResult(val value: ByteArray, val status: GattStatus) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattResult) return false
        return value.contentEquals(other.value) && status == other.status
    }
    override fun hashCode(): Int = 31 * value.contentHashCode() + status.hashCode()
}

internal class PendingSlot<T>(private val name: String) {
    private var deferred: CompletableDeferred<T>? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): CompletableDeferred<T>? = deferred

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: CompletableDeferred<T>?) {
        check(deferred == null || value == null) { "$name overwritten while pending" }
        deferred = value
    }

    fun cancelAndClear(cause: Throwable) {
        deferred?.completeExceptionally(cause)
        deferred = null
    }
}

internal class PendingOperations {
    private val _characteristicRead = PendingSlot<GattResult>("characteristicRead")
    var characteristicRead: CompletableDeferred<GattResult>? by _characteristicRead

    private val _characteristicWrite = PendingSlot<GattStatus>("characteristicWrite")
    var characteristicWrite: CompletableDeferred<GattStatus>? by _characteristicWrite

    private val _descriptorRead = PendingSlot<GattResult>("descriptorRead")
    var descriptorRead: CompletableDeferred<GattResult>? by _descriptorRead

    private val _descriptorWrite = PendingSlot<GattStatus>("descriptorWrite")
    var descriptorWrite: CompletableDeferred<GattStatus>? by _descriptorWrite

    private val _rssiRead = PendingSlot<Int>("rssiRead")
    var rssiRead: CompletableDeferred<Int>? by _rssiRead

    private val _mtuRequest = PendingSlot<Int>("mtuRequest")
    var mtuRequest: CompletableDeferred<Int>? by _mtuRequest

    fun cancelAll(cause: Throwable) {
        _characteristicRead.cancelAndClear(cause)
        _characteristicWrite.cancelAndClear(cause)
        _descriptorRead.cancelAndClear(cause)
        _descriptorWrite.cancelAndClear(cause)
        _rssiRead.cancelAndClear(cause)
        _mtuRequest.cancelAndClear(cause)
    }
}
