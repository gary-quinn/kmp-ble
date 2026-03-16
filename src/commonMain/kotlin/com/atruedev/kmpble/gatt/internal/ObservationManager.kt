package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class ObservationManager {

    private val flows = mutableMapOf<Characteristic, MutableSharedFlow<ByteArray>>()

    fun getOrCreateFlow(characteristic: Characteristic): MutableSharedFlow<ByteArray> {
        return flows.getOrPut(characteristic) {
            MutableSharedFlow(extraBufferCapacity = 64)
        }
    }

    // Matches by UUID, not object identity. For duplicate-UUID characteristics within
    // a service, both receive the notification — matches BLE spec behavior where
    // notifications are per-UUID, not per-handle.
    fun emitByUuid(serviceUuid: Uuid, charUuid: Uuid, value: ByteArray) {
        for ((char, flow) in flows) {
            if (char.serviceUuid == serviceUuid && char.uuid == charUuid) {
                flow.tryEmit(value)
                return
            }
        }
    }

    fun clear() {
        flows.clear()
    }
}

internal fun <T> Flow<T>.applyBackpressure(strategy: BackpressureStrategy): Flow<T> =
    when (strategy) {
        is BackpressureStrategy.Latest -> conflate()
        is BackpressureStrategy.Buffer -> buffer(strategy.capacity, BufferOverflow.DROP_OLDEST)
        is BackpressureStrategy.Unbounded -> buffer(Channel.UNLIMITED)
    }
