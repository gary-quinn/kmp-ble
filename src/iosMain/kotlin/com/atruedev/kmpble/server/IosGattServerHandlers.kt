package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.emptyBleData
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheralManager
import platform.Foundation.NSError
import kotlin.uuid.Uuid

/**
 * Delegate callback handlers for [IosGattServer].
 */

internal fun IosGattServer.handleServiceAdded(error: NSError?) {
    pendingServiceAdd?.complete(error)
}

internal fun IosGattServer.handleReadRequest(
    peripheral: CBPeripheralManager,
    request: CBATTRequest,
) {
    scope.launch {
        trackCentral(request.central)

        val handler = readHandlers[request.charUuid]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    request.centralId,
                    "read-rejected (no handler)",
                    request.charUuid,
                    GattStatus.ReadNotPermitted,
                ),
            )
            peripheral.respondToRequest(request, withResult = CBATTErrorRequestNotSupported)
            return@launch
        }

        try {
            val bleData = handler(request.centralId)
            val offset = request.offset.toInt()

            val responseNsData =
                when {
                    offset >= bleData.size && offset > 0 -> emptyBleData().nsData
                    offset > 0 -> bleData.slice(offset, bleData.size).nsData
                    else -> bleData.nsData
                }
            request.value = responseNsData
            peripheral.respondToRequest(request, withResult = CBATTErrorSuccess)

            logEvent(
                BleLogEvent.ServerRequest(
                    request.centralId,
                    "read (${responseNsData.length.toInt()}B, offset=$offset)",
                    request.charUuid,
                    GattStatus.Success,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logEvent(
                BleLogEvent.ServerRequest(
                    request.centralId,
                    "read-failed (handler threw)",
                    request.charUuid,
                    GattStatus.Failure,
                ),
            )
            peripheral.respondToRequest(request, withResult = CB_ATT_ERROR_UNLIKELY)
        }
    }
}

internal fun IosGattServer.handleWriteRequests(
    peripheral: CBPeripheralManager,
    rawRequests: List<*>,
) {
    val requests = rawRequests.filterIsInstance<CBATTRequest>()
    if (requests.isEmpty()) return

    val firstRequest = requests.first()

    scope.launch {
        // CoreBluetooth delivers fragmented writes as a batch with non-zero offsets
        val hasFragments = requests.any { it.offset.toInt() > 0 }
        val writes: List<Pair<Uuid, BleData>> =
            if (hasFragments) {
                val assembled = assembleFragmentedWrites(requests)
                if (assembled == null) {
                    peripheral.respondToRequest(
                        firstRequest,
                        withResult = CBATTErrorInvalidAttributeValueLength,
                    )
                    return@launch
                }
                assembled
            } else {
                requests.map { request ->
                    val data = request.value?.let(::bleDataFromNSData) ?: emptyBleData()
                    request.charUuid to data
                }
            }

        val errorCode = dispatchWrites(requests.first().centralId, requests.first().central, writes)
        peripheral.respondToRequest(firstRequest, withResult = errorCode ?: CBATTErrorSuccess)
    }
}

internal suspend fun IosGattServer.dispatchWrites(
    centralId: Identifier,
    central: CBCentral,
    writes: List<Pair<Uuid, BleData>>,
): Long? {
    trackCentral(central)

    for ((charUuid, data) in writes) {
        val handler = writeHandlers[charUuid]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    centralId,
                    "write-rejected (no handler)",
                    charUuid,
                    GattStatus.WriteNotPermitted,
                ),
            )
            return CBATTErrorWriteNotPermitted
        }

        try {
            val status = handler(centralId, data, true)
            if (status != null && status != GattStatus.Success) {
                return status.toCBATTError()
            }
            logEvent(BleLogEvent.ServerRequest(centralId, "write (${data.size}B)", charUuid, status))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logEvent(
                BleLogEvent.ServerRequest(centralId, "write-failed (handler threw)", charUuid, GattStatus.Failure),
            )
            return CB_ATT_ERROR_UNLIKELY
        }
    }
    return null
}

internal fun assembleFragmentedWrites(requests: List<CBATTRequest>): List<Pair<Uuid, BleData>>? {
    val fragments =
        requests.map { request ->
            val nsData = request.value
            val bytes = if (nsData != null) bleDataFromNSData(nsData).toByteArray() else byteArrayOf()
            WriteFragment(request.charUuid, request.offset.toInt(), bytes)
        }
    return when (val result = assembleWriteFragments(fragments)) {
        is AssemblyResult.Success -> result.writes.map { it.charUuid to BleData(it.data) }
        is AssemblyResult.PayloadTooLarge -> {
            logEvent(
                BleLogEvent.ServerRequest(
                    requests.first().centralId,
                    "write-rejected (${result.actualSize}B exceeds limit)",
                    result.charUuid,
                    GattStatus.InvalidAttributeLength,
                ),
            )
            null
        }
    }
}

internal fun IosGattServer.handleSubscribe(
    central: CBCentral,
    characteristic: CBCharacteristic,
) {
    scope.launch {
        val charUuid = characteristic.UUID.UUIDString
        val centralId = Identifier(central.id)
        val isNewCentral = trackCentral(central)

        subscriptions.getOrPut(charUuid) { mutableSetOf() }.add(central.id)

        logEvent(
            BleLogEvent.ServerClientEvent(centralId, "subscribed to $charUuid"),
        )

        if (isNewCentral) {
            if (!_connectionEvents.tryEmit(ServerConnectionEvent.Connected(centralId))) {
                logEvent(
                    BleLogEvent.Error(
                        centralId,
                        "Connection event buffer full, event dropped",
                        null,
                    ),
                )
            }
        }
    }
}

internal fun IosGattServer.handleReadyToUpdate() {
    readyToUpdate.complete(Unit)
}
