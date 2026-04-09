package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Observation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun <T> ObservationManager.buildObservationFlow(
    serviceUuid: Uuid,
    charUuid: Uuid,
    backpressure: BackpressureStrategy,
    isReady: () -> Boolean,
    enableNotifications: suspend () -> Unit,
    disableNotifications: () -> Unit,
    mapper: suspend FlowCollector<T>.(ObservationEvent) -> Unit,
): Flow<T> =
    flow {
        val eventFlow = subscribe(serviceUuid, charUuid, backpressure)
        eventFlow.collect { event -> mapper(event) }
    }.onStart {
        if (isReady()) enableNotifications()
    }.applyBackpressure(backpressure)
        .onCompletion {
            val wasLastCollector = unsubscribe(serviceUuid, charUuid)
            if (wasLastCollector) disableNotifications()
        }

@OptIn(ExperimentalUuidApi::class)
internal fun ObservationManager.observationFlow(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
    isReady: () -> Boolean,
    enableNotifications: suspend () -> Unit,
    disableNotifications: () -> Unit,
): Flow<Observation> = buildObservationFlow(
    serviceUuid = characteristic.serviceUuid,
    charUuid = characteristic.uuid,
    backpressure = backpressure,
    isReady = isReady,
    enableNotifications = enableNotifications,
    disableNotifications = disableNotifications,
) { event ->
    when (event) {
        is ObservationEvent.Value -> emit(Observation.Value(event.data))
        is ObservationEvent.Disconnected -> emit(Observation.Disconnected)
        is ObservationEvent.PermanentlyDisconnected -> emit(Observation.Disconnected)
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun ObservationManager.observationValuesFlow(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
    isReady: () -> Boolean,
    enableNotifications: suspend () -> Unit,
    disableNotifications: () -> Unit,
): Flow<ByteArray> = buildObservationFlow(
    serviceUuid = characteristic.serviceUuid,
    charUuid = characteristic.uuid,
    backpressure = backpressure,
    isReady = isReady,
    enableNotifications = enableNotifications,
    disableNotifications = disableNotifications,
) { event ->
    when (event) {
        is ObservationEvent.Value -> emit(event.data)
        is ObservationEvent.Disconnected -> Unit
        is ObservationEvent.PermanentlyDisconnected -> Unit
    }
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun ObservationManager.resubscribe(
    findCharacteristic: (serviceUuid: Uuid, charUuid: Uuid) -> Characteristic?,
    enableNotifications: suspend (Characteristic) -> Unit,
) {
    val toResubscribe = getObservationsToResubscribe()
    for (key in toResubscribe) {
        val char = findCharacteristic(key.serviceUuid, key.charUuid)
        if (char != null) {
            enableNotifications(char)
        } else {
            completeObservation(key)
        }
    }
}
