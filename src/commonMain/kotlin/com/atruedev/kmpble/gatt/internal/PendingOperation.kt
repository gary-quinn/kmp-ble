package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.error.GattStatus
import kotlinx.coroutines.CompletableDeferred

internal data class GattResult(val value: ByteArray, val status: GattStatus) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattResult) return false
        return value.contentEquals(other.value) && status == other.status
    }
    override fun hashCode(): Int = 31 * value.contentHashCode() + status.hashCode()
}

internal class PendingOperations {
    private var _characteristicRead: CompletableDeferred<GattResult>? = null
    private var _characteristicWrite: CompletableDeferred<GattStatus>? = null
    private var _descriptorRead: CompletableDeferred<GattResult>? = null
    private var _descriptorWrite: CompletableDeferred<GattStatus>? = null
    private var _rssiRead: CompletableDeferred<Int>? = null
    private var _mtuRequest: CompletableDeferred<Int>? = null

    var characteristicRead: CompletableDeferred<GattResult>?
        get() = _characteristicRead
        set(value) {
            check(_characteristicRead == null || value == null) {
                "characteristicRead overwritten while pending"
            }
            _characteristicRead = value
        }

    var characteristicWrite: CompletableDeferred<GattStatus>?
        get() = _characteristicWrite
        set(value) {
            check(_characteristicWrite == null || value == null) {
                "characteristicWrite overwritten while pending"
            }
            _characteristicWrite = value
        }

    var descriptorRead: CompletableDeferred<GattResult>?
        get() = _descriptorRead
        set(value) {
            check(_descriptorRead == null || value == null) {
                "descriptorRead overwritten while pending"
            }
            _descriptorRead = value
        }

    var descriptorWrite: CompletableDeferred<GattStatus>?
        get() = _descriptorWrite
        set(value) {
            check(_descriptorWrite == null || value == null) {
                "descriptorWrite overwritten while pending"
            }
            _descriptorWrite = value
        }

    var rssiRead: CompletableDeferred<Int>?
        get() = _rssiRead
        set(value) {
            check(_rssiRead == null || value == null) {
                "rssiRead overwritten while pending"
            }
            _rssiRead = value
        }

    var mtuRequest: CompletableDeferred<Int>?
        get() = _mtuRequest
        set(value) {
            check(_mtuRequest == null || value == null) {
                "mtuRequest overwritten while pending"
            }
            _mtuRequest = value
        }

    fun cancelAll(cause: Throwable) {
        _characteristicRead?.completeExceptionally(cause)
        _characteristicWrite?.completeExceptionally(cause)
        _descriptorRead?.completeExceptionally(cause)
        _descriptorWrite?.completeExceptionally(cause)
        _rssiRead?.completeExceptionally(cause)
        _mtuRequest?.completeExceptionally(cause)
        _characteristicRead = null
        _characteristicWrite = null
        _descriptorRead = null
        _descriptorWrite = null
        _rssiRead = null
        _mtuRequest = null
    }
}
