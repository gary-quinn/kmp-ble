@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.backend.GattServiceRecord
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.PeripheralEvent
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.error.PeripheralClosed
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

internal val BACKEND_DISCONNECT_TIMEOUT = 5.seconds

internal suspend fun BackendPeripheral.connectInternal(options: ConnectionOptions) {
    withContext(context.dispatcher) {
        when (val current = context.state.value) {
            is State.Connected -> return@withContext
            is State.Connecting, is State.Disconnecting ->
                throw BleException(OperationFailed("connect() is already in progress (state: ${current.displayName})"))
            is State.Disconnected -> Unit
        }
        timeouts = options.timeouts
        servicesChangedWhileConnecting = false
        linkLossDuringConnect = null
        linkOwnedByConnect = true
        context.processEvent(ConnectionEvent.ConnectRequested)
        context.gattQueue.start(options.gattOperationTimeout)
        try {
            establishLink(options)
            authenticate(options)
            var discovered = discoverDuringConnect()
            var rediscoveries = 0
            while (servicesChangedWhileConnecting && rediscoveries++ < MAX_CONNECT_REDISCOVERIES) {
                servicesChangedWhileConnecting = false
                discovered = discoverDuringConnect()
            }
            context.processEvent(ConnectionEvent.ServicesDiscovered)
            context.updateServices(discovered)
            context.updateMtu(transport.mtu())
            ensureStillConnecting()
            context.processEvent(ConnectionEvent.ConfigurationComplete)
        } catch (e: Throwable) {
            if (isClosed) throw BleException(PeripheralClosed())
            val error = e.toConnectError()
            try {
                withContext(NonCancellable) { abortConnect(error) }
            } catch (abortFailure: IllegalStateException) {
                if (!isClosed) throw abortFailure
            }
            if (isClosed) throw BleException(PeripheralClosed())
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw e as? BleException ?: BleException(error)
        } finally {
            linkOwnedByConnect = false
        }
        context.scope.launch { resubscribeObservations() }
        if (servicesChangedWhileConnecting) onServicesChanged()
    }
}

internal suspend fun BackendPeripheral.disconnectInternal() {
    withContext(context.dispatcher) {
        if (context.state.value is State.Disconnected) return@withContext
        withTimeoutOrNull(PENDING_DISABLE_FLUSH_TIMEOUT) { pendingDisables.toList().joinAll() }
        context.processEvent(ConnectionEvent.DisconnectRequested)
        disconnectTransportBestEffort("disconnect")
        onLinkCleanup()
        if (context.state.value !is State.Disconnected) {
            context.processEvent(ConnectionEvent.ConnectionLost(OperationFailed("disconnect")))
        }
    }
}

internal suspend fun BackendPeripheral.handleEvent(event: PeripheralEvent) {
    when (event) {
        is PeripheralEvent.ValueChanged -> {
            val characteristic = charsByHandle[event.characteristic] ?: return
            observationManager.emitByUuid(characteristic.serviceUuid, characteristic.uuid, event.value)
        }
        is PeripheralEvent.Disconnected -> onLinkDown(event.error, adapterOff = false)
        PeripheralEvent.AdapterOff -> onLinkDown(error = null, adapterOff = true)
        PeripheralEvent.ServicesChanged -> onServicesChanged()
        is PeripheralEvent.MtuChanged -> context.updateMtu(event.mtu)
        is PeripheralEvent.BondStateChanged -> context.updateBondState(event.state)
    }
}

private suspend fun BackendPeripheral.establishLink(options: ConnectionOptions) {
    try {
        withTimeout(options.timeouts.connect) { transport.connect(options) }
    } catch (e: TimeoutCancellationException) {
        throw BleException(
            ConnectionFailed("Connection timeout after ${options.timeouts.connect}", ConnectionFailureReason.TIMEOUT),
        )
    }
    linkUp = true
    ensureStillConnecting()
}

private suspend fun BackendPeripheral.authenticate(options: ConnectionOptions) {
    val bondRequired =
        options.bondingPreference == BondingPreference.Required && transport.features.supportsBonding
    val currentBond = transport.bondState()
    context.updateBondState(currentBond)
    if (!bondRequired || currentBond == BondState.Bonded) {
        context.processEvent(ConnectionEvent.LinkEstablished)
        return
    }
    context.processEvent(ConnectionEvent.BondRequired)
    context.updateBondState(BondState.Bonding)
    logEvent(BleLogEvent.BondEvent(identifier, "bonding"))
    try {
        transport.createBond()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val error =
            (e as? BleException)?.error
                ?: ConnectionFailed("Bonding failed: ${e.message}", ConnectionFailureReason.BONDING_FAILED)
        context.updateBondState(BondState.NotBonded)
        logEvent(BleLogEvent.BondEvent(identifier, "bonding failed: $error"))
        throw BleException(error)
    }
    context.updateBondState(BondState.Bonded)
    logEvent(BleLogEvent.BondEvent(identifier, "bonded"))
    ensureStillConnecting()
    context.processEvent(ConnectionEvent.BondSucceeded)
}

private suspend fun BackendPeripheral.discoverDuringConnect(): List<DiscoveredService> {
    val records =
        try {
            context.gattQueue.enqueue(timeout = timeouts.serviceDiscovery) { transport.discoverServices() }
        } catch (e: TimeoutCancellationException) {
            throw BleException(OperationFailed("Service discovery timeout after ${timeouts.serviceDiscovery}"))
        } catch (e: NotConnectedException) {
            ensureStillConnecting()
            throw BleException(ConnectionLost("Link lost during service discovery", ConnectionFailureReason.LINK_LOSS))
        }
    ensureStillConnecting()
    return installServices(records)
}

private fun BackendPeripheral.ensureStillConnecting() {
    linkLossDuringConnect?.let { throw BleException(it) }
    val current = context.state.value
    if (current is State.Disconnected) {
        throw BleException(ConnectionFailed("Connection interrupted (${current.displayName})"))
    }
}

private suspend fun BackendPeripheral.abortConnect(error: BleError) {
    disconnectTransportBestEffort("abort connect")
    onLinkCleanup()
    val current = context.state.value
    if (current is State.Disconnected) return
    val event =
        when (current) {
            is State.Connecting.Discovering -> ConnectionEvent.DiscoveryFailed(error)
            is State.Connecting.Authenticating -> ConnectionEvent.BondFailed(error)
            else -> ConnectionEvent.ConnectionLost(error)
        }
    context.processEvent(event)
    driveToDisconnected(error)
}

private suspend fun BackendPeripheral.disconnectTransportBestEffort(reason: String) {
    try {
        withTimeout(BACKEND_DISCONNECT_TIMEOUT) { transport.disconnect() }
    } catch (e: TimeoutCancellationException) {
        logEvent(
            BleLogEvent.Warning(
                identifier,
                "$reason: transport disconnect timed out after $BACKEND_DISCONNECT_TIMEOUT",
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logEvent(BleLogEvent.Warning(identifier, "$reason: transport disconnect failed: ${e.message}"))
    }
}

private suspend fun BackendPeripheral.driveToDisconnected(error: BleError) {
    repeat(MAX_TERMINAL_TRANSITIONS) {
        if (context.state.value is State.Disconnected) return
        context.processEvent(ConnectionEvent.ConnectionLost(error))
    }
}

private suspend fun BackendPeripheral.onLinkDown(
    error: BleError?,
    adapterOff: Boolean,
) {
    val loss = error ?: ConnectionLost("Remote disconnect", ConnectionFailureReason.LINK_LOSS)
    if (linkOwnedByConnect) {
        linkLossDuringConnect = if (adapterOff) ConnectionFailed("Bluetooth adapter turned off") else loss
        return
    }
    val current = context.state.value
    if (current is State.Disconnected) return
    when {
        adapterOff -> context.processEvent(ConnectionEvent.AdapterOff)
        current is State.Disconnecting.Requested ->
            context.processEvent(ConnectionEvent.ConnectionLost(OperationFailed("disconnect")))
        else -> driveToDisconnected(loss)
    }
    onLinkCleanup()
}

internal fun BackendPeripheral.onLinkCleanup() {
    charHandles.clear()
    charsByHandle.clear()
    descHandles.clear()
    l2capChannels.forEach { it.onLinkLost() }
    l2capChannels.clear()
    if (linkUp) {
        linkUp = false
        observationManager.onDisconnect()
    }
}

internal suspend fun BackendPeripheral.onServicesChanged() {
    if (linkOwnedByConnect) {
        servicesChangedWhileConnecting = true
        return
    }
    if (context.state.value !is State.Connected.Ready) return
    context.processEvent(ConnectionEvent.ServiceChangedIndication)
    context.scope.launch {
        try {
            context.gattQueue.enqueueBle(timeouts.serviceDiscovery) { rediscover() }
            if (context.state.value is State.Connected.ServiceChanged) {
                context.processEvent(ConnectionEvent.RediscoverySucceeded)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = (e as? BleException)?.error ?: OperationFailed("Rediscovery failed: ${e.message}")
            if (context.state.value is State.Connected.ServiceChanged) {
                context.processEvent(ConnectionEvent.RediscoveryFailed(error))
            }
            disconnectTransportBestEffort("rediscovery failed")
        }
    }
}

internal suspend fun BackendPeripheral.rediscover(): List<DiscoveredService> {
    val services = installServices(transport.discoverServices())
    context.updateServices(services)
    context.updateMtu(transport.mtu())
    context.scope.launch { resubscribeObservations() }
    return services
}

@OptIn(ExperimentalUuidApi::class)
internal fun BackendPeripheral.installServices(records: List<GattServiceRecord>): List<DiscoveredService> {
    charHandles.clear()
    charsByHandle.clear()
    descHandles.clear()
    return records.map { service ->
        DiscoveredService(
            uuid = service.uuid,
            characteristics =
                service.characteristics.map { record ->
                    val descriptors = mutableListOf<Descriptor>()
                    val characteristic = Characteristic(service.uuid, record.uuid, record.properties, descriptors)
                    for (descriptorRecord in record.descriptors) {
                        val descriptor = Descriptor(characteristic, descriptorRecord.uuid)
                        descriptors += descriptor
                        descHandles[descriptor] = descriptorRecord.handle
                    }
                    charHandles[characteristic] = record.handle
                    charsByHandle[record.handle] = characteristic
                    characteristic
                },
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun BackendPeripheral.resubscribeObservations() {
    for (key in observationManager.getObservationsToResubscribe()) {
        val characteristic = services.value.findCharacteristic(key.serviceUuid, key.charUuid)
        if (characteristic == null) {
            observationManager.completeObservation(key)
            continue
        }
        try {
            enableNotifications(characteristic)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEvent(
                BleLogEvent.Warning(identifier, "Re-enabling notifications for ${key.charUuid} failed: ${e.message}"),
            )
        }
    }
}

private fun Throwable.toConnectError(): BleError =
    when (this) {
        is BleException -> error
        is TimeoutCancellationException -> ConnectionFailed("Connection timeout", ConnectionFailureReason.TIMEOUT)
        is CancellationException -> ConnectionFailed("Connection cancelled")
        else -> ConnectionFailed(message ?: "Connection failed", ConnectionFailureReason.GATT_ERROR)
    }

private const val MAX_TERMINAL_TRANSITIONS = 2
private const val MAX_CONNECT_REDISCOVERIES = 2
private val PENDING_DISABLE_FLUSH_TIMEOUT = 1.seconds
