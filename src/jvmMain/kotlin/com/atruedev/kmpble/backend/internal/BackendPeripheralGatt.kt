@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi

internal suspend fun BackendPeripheral.readInternal(characteristic: Characteristic): ByteArray {
    checkOpen()
    return context.gattQueue.enqueueBle(timeout = timeouts.read) {
        transport.read(requireCharHandle(characteristic))
    }
}

internal suspend fun BackendPeripheral.writeInternal(
    characteristic: Characteristic,
    data: ByteArray,
    writeType: WriteType,
) {
    checkOpen()
    val maxLength = maximumWriteValueLength.value
    LargeWriteHandler.validateForWriteType(data, maxLength, writeType)
    val chunks = LargeWriteHandler.chunk(data, maxLength)
    context.gattQueue.enqueueBle(timeout = timeouts.write) {
        val handle = requireCharHandle(characteristic)
        for (chunk in chunks) transport.write(handle, chunk, writeType)
    }
}

internal suspend fun BackendPeripheral.writeReliableInternal(
    characteristic: Characteristic,
    data: ByteArray,
) {
    checkOpen()
    if (!supportsReliableWrite) {
        throw UnsupportedOperationException(
            "writeReliable is not supported by this JVM backend. Check Peripheral.supportsReliableWrite and use write().",
        )
    }
    context.gattQueue.enqueueBle(timeout = timeouts.reliableWrite) {
        transport.writeReliable(requireCharHandle(characteristic), data)
    }
}

internal suspend fun BackendPeripheral.readDescriptorInternal(descriptor: Descriptor): ByteArray {
    checkOpen()
    return context.gattQueue.enqueueBle(timeout = timeouts.read) {
        transport.readDescriptor(requireDescHandle(descriptor))
    }
}

internal suspend fun BackendPeripheral.writeDescriptorInternal(
    descriptor: Descriptor,
    data: ByteArray,
) {
    checkOpen()
    context.gattQueue.enqueueBle(timeout = timeouts.write) {
        transport.writeDescriptor(requireDescHandle(descriptor), data)
    }
}

internal suspend fun BackendPeripheral.readRssiInternal(): Int {
    checkOpen()
    return context.gattQueue.enqueueBle { transport.readRssi() }
}

internal suspend fun BackendPeripheral.requestMtuInternal(mtu: Int): Int {
    checkOpen()
    require(mtu >= MIN_ATT_MTU) { "MTU must be >= $MIN_ATT_MTU, was $mtu" }
    return context.gattQueue.enqueueBle(timeout = timeouts.mtuNegotiation) {
        transport.mtu().also { context.updateMtu(it) }
    }
}

internal suspend fun BackendPeripheral.refreshServicesInternal(): List<DiscoveredService> {
    checkOpen()
    if (context.state.value !is State.Connected) {
        throw BleException(ConnectionLost("Peripheral is not connected", ConnectionFailureReason.LINK_LOSS))
    }
    return context.gattQueue.enqueueBle(timeout = timeouts.serviceDiscovery) { rediscover() }
}

internal fun BackendPeripheral.observeInternal(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
): Flow<Observation> {
    checkOpen()
    return buildObservationFlow(
        characteristic = characteristic,
        backpressure = backpressure,
        observationManager = observationManager,
        isReady = { context.state.value is State.Connected.Ready },
        enable = { enableNotifications(it) },
        disable = { disableNotificationsBestEffort(it) },
        mapper = ObservationToObservation,
    )
}

internal fun BackendPeripheral.observeValuesInternal(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
): Flow<ByteArray> {
    checkOpen()
    return buildObservationFlow(
        characteristic = characteristic,
        backpressure = backpressure,
        observationManager = observationManager,
        isReady = { context.state.value is State.Connected.Ready },
        enable = { enableNotifications(it) },
        disable = { disableNotificationsBestEffort(it) },
        mapper = ObservationToBytes,
    )
}

internal suspend fun BackendPeripheral.enableNotifications(characteristic: Characteristic) {
    context.gattQueue.enqueueBle {
        transport.setNotify(requireCharHandle(characteristic), enabled = true)
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun BackendPeripheral.disableNotificationsBestEffort(characteristic: Characteristic) {
    if (isClosed || context.state.value !is State.Connected) return
    val job =
        context.scope.launch {
            try {
                context.gattQueue.enqueue {
                    val handle = charHandles[characteristic] ?: return@enqueue
                    transport.setNotify(handle, enabled = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (context.state.value !is State.Connected) return@launch
                logEvent(
                    BleLogEvent.Warning(
                        identifier,
                        "Disabling notifications for ${characteristic.uuid} failed: ${e.message}",
                    ),
                )
            }
        }
    pendingDisables += job
    job.invokeOnCompletion { pendingDisables -= job }
}

internal suspend fun BackendPeripheral.openL2capChannelInternal(
    psm: Int,
    secure: Boolean,
    mtu: Int?,
): L2capChannel {
    checkOpen()
    if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }
    if (!transport.features.supportsL2cap) {
        throw L2capException.NotSupported("L2CAP channels are not supported by this JVM backend")
    }
    val current = context.state.value
    if (current !is State.Connected.Ready) {
        throw L2capException.NotConnected("Peripheral is not connected and ready (state: $current)")
    }
    val stream =
        try {
            withTimeout(timeouts.l2capOpen) { transport.openL2capChannel(psm, secure) }
        } catch (e: TimeoutCancellationException) {
            throw L2capException.OpenFailed(psm, "timed out after ${timeouts.l2capOpen}", e)
        }
    val channel =
        BackendL2capChannel(
            stream = stream,
            mtu = mtu ?: stream.mtu,
            recovery = L2capRecoveryContext { openL2capChannelInternal(psm, secure, mtu) },
        )
    l2capChannels += channel
    channel.onClosed { l2capChannels.remove(channel) }
    return channel
}

internal fun BackendPeripheral.requireCharHandle(characteristic: Characteristic): Long =
    charHandles[characteristic] ?: throw BleException(StaleGattHandle("characteristic", characteristic.uuid.toString()))

internal fun BackendPeripheral.requireDescHandle(descriptor: Descriptor): Long =
    descHandles[descriptor] ?: throw BleException(StaleGattHandle("descriptor", descriptor.uuid.toString()))

private const val MIN_ATT_MTU = 23
