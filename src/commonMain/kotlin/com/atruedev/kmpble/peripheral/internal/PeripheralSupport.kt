package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.internal.ObservationEvent
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.gatt.internal.applyBackpressure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun List<DiscoveredService>?.findCharacteristic(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
): Characteristic? =
    this
        ?.firstOrNull { it.uuid == serviceUuid }
        ?.characteristics
        ?.firstOrNull { it.uuid == characteristicUuid }

@OptIn(ExperimentalUuidApi::class)
internal fun List<DiscoveredService>?.findDescriptor(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    descriptorUuid: Uuid,
): Descriptor? =
    findCharacteristic(serviceUuid, characteristicUuid)
        ?.descriptors
        ?.firstOrNull { it.uuid == descriptorUuid }

/**
 * Submit a native GATT call and await its asynchronous result via [PendingOperations].
 *
 * The pending slot is set before submission, so a callback that fires synchronously
 * still finds a deferred to complete. If the platform reports rejection ([submit]
 * returns false), the slot is cleared and the caller is failed deterministically.
 *
 * Confined to the peripheral's serial dispatcher.
 */
internal suspend fun <T> PendingOperations.awaitGatt(
    op: PendingOp<T>,
    label: String,
    submit: () -> Boolean,
): T {
    val deferred = CompletableDeferred<T>()
    set(op, deferred)
    if (!submit()) {
        clear(op)
        throw BleException(GattError(label, GattStatus.Failure))
    }
    return deferred.await()
}

/**
 * Builds the standard `observe()` flow shape used by every platform peripheral:
 *   subscribe -> map events -> apply backpressure -> unsubscribe + maybe disable CCCD.
 *
 * The CCCD enable/disable side is platform-specific and supplied by [enable] and
 * [disable]. State checks for "ready before enabling" are also injected so that
 * observation flows can be started before the peripheral connects.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun <T> buildObservationFlow(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
    observationManager: ObservationManager,
    isReady: () -> Boolean,
    enable: suspend (Characteristic) -> Unit,
    disable: suspend (Characteristic) -> Unit,
    mapper: suspend FlowCollector<T>.(ObservationEvent) -> Unit,
): Flow<T> {
    val serviceUuid = characteristic.serviceUuid
    val charUuid = characteristic.uuid

    return flow {
        observationManager
            .subscribe(serviceUuid, charUuid, backpressure)
            .collect { event -> mapper(event) }
    }.onStart {
        if (isReady()) enable(characteristic)
    }.applyBackpressure(backpressure)
        .onCompletion {
            val wasLastCollector = observationManager.unsubscribe(serviceUuid, charUuid)
            if (wasLastCollector) disable(characteristic)
        }
}

internal val ObservationToObservation: suspend FlowCollector<Observation>.(ObservationEvent) -> Unit = { event ->
    when (event) {
        is ObservationEvent.Value -> emit(Observation.Value(event.data))
        is ObservationEvent.Disconnected -> emit(Observation.Disconnected)
        is ObservationEvent.PermanentlyDisconnected -> emit(Observation.Disconnected)
    }
}

internal val ObservationToBytes: suspend FlowCollector<ByteArray>.(ObservationEvent) -> Unit = { event ->
    if (event is ObservationEvent.Value) emit(event.data)
}
