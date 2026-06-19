package com.atruedev.kmpble.testing

import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.internal.ObservationManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal suspend fun FakePeripheral.simulateEvent(event: ConnectionEvent): State =
    connectionSimulator.simulateEvent(event)

/** Drives the state machine from [State.Connected.Ready] to [State.Connected.BondingChange]. */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.simulateBondStateChange() {
    connectionSimulator.simulateBondStateChange()
}

/**
 * Drives the state machine from [State.Connected.Ready] to
 * [State.Connected.ServiceChanged]. Services remain populated (now stale)
 * until rediscovery completes.
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.simulateServiceChangedIndication() {
    connectionSimulator.simulateServiceChangedIndication()
}

/**
 * Drives the state machine from [State.Connected.ServiceChanged] back to
 * [State.Connected.Ready].
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.simulateRediscoverySucceeded() {
    connectionSimulator.simulateRediscoverySucceeded()
}

/**
 * Simulate a disconnect event. This will emit
 * [com.atruedev.kmpble.gatt.Observation.Disconnected] to all active
 * observations but NOT complete them -- they persist for reconnection.
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.simulateDisconnect(error: BleError = ConnectionLost("Simulated disconnect")) {
    connectionSimulator.simulateDisconnect(error)
}

/**
 * Simulate a reconnection. This will:
 * 1. Transition through connecting states
 * 2. Re-discover services
 * 3. Re-enable CCCD for all active observations
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.simulateReconnect(newServices: List<DiscoveredService>? = null) {
    connectionSimulator.simulateReconnect(newServices)
}

/**
 * Simulate permanent disconnect (max reconnection attempts exhausted).
 * This will complete all observation flows.
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.simulatePermanentDisconnect() {
    connectionSimulator.simulatePermanentDisconnect()
}

/** Simulate a characteristic notification by emitting [value] to active observers. */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.emitObservationValue(
    serviceUuid: Uuid,
    charUuid: Uuid,
    value: ByteArray,
) {
    connectionSimulator.emitObservationValue(serviceUuid, charUuid, value)
}

/** Convenience overload accepting short or full UUID strings. */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.emitObservationValue(
    serviceUuid: String,
    charUuid: String,
    value: ByteArray,
) {
    connectionSimulator.emitObservationValue(serviceUuid, charUuid, value)
}

/** Configure the PHY values returned by [com.atruedev.kmpble.peripheral.Peripheral.readPhy]. */
@OptIn(ExperimentalUuidApi::class)
public fun FakePeripheral.configurePhy(
    tx: Phy,
    rx: Phy,
) {
    gattResponder.configurePhy(tx, rx)
}

/** Simulate a spontaneous PHY update, which emits to [com.atruedev.kmpble.peripheral.Peripheral.phyUpdate]. */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.emitPhyUpdate(
    tx: Phy,
    rx: Phy,
) {
    gattResponder.emitPhyUpdate(tx, rx)
}

/** Returns all CCCD writes recorded during observation setup/teardown. */
@OptIn(ExperimentalUuidApi::class)
public fun FakePeripheral.getCccdWrites(): List<FakePeripheral.CccdWrite> = cccdWritesState.value

/** Clears recorded CCCD writes. */
@OptIn(ExperimentalUuidApi::class)
public fun FakePeripheral.clearCccdWrites() {
    cccdWritesState.value = emptyList()
}

/** Returns `true` if there are active collectors for the given characteristic. */
@OptIn(ExperimentalUuidApi::class)
public suspend fun FakePeripheral.hasCollectors(
    serviceUuid: Uuid,
    charUuid: Uuid,
): Boolean = connectionSimulator.hasCollectors(serviceUuid, charUuid)

/** Expose ObservationManager for test debugging. */
@OptIn(ExperimentalUuidApi::class)
internal fun FakePeripheral.getObservationManagerForTest(): ObservationManager = observationManager
