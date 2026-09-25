package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.ScanRecord
import com.atruedev.kmpble.backend.ScanRequest
import com.atruedev.kmpble.backend.ScanTransport
import com.atruedev.kmpble.macos.Macos
import com.atruedev.kmpble.scanner.ScanFailedException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * CoreBluetooth discovery with duplicates allowed, like the iOS scanner. Service filtering
 * runs in core, so the native scan is always unfiltered.
 */
internal class MacosScanTransport(
    private val stack: MacosStack,
    private val settleTimeout: Duration = SETTLE_TIMEOUT,
) : ScanTransport {
    override fun scan(request: ScanRequest): Flow<ScanRecord> =
        callbackFlow {
            val state =
                try {
                    stack.awaitCentralSettled(settleTimeout)
                } catch (e: MissingUsageDescriptionException) {
                    throw ScanFailedException(Macos.ERROR_USAGE_DESCRIPTION_MISSING, e.message)
                } catch (e: UnsatisfiedLinkError) {
                    throw ScanFailedException(Macos.ERROR_NATIVE_UNAVAILABLE, e.message)
                }
            if (state != CbManagerState.POWERED_ON) throw scanFailure(state)

            val registration = stack.addScanListener { event -> trySend(event.toScanRecord()) }
            launch {
                val next = stack.centralState.first { it != CbManagerState.POWERED_ON }
                close(scanFailure(next))
            }
            awaitClose { registration.close() }
        }

    private fun scanFailure(state: Int): ScanFailedException =
        when (state) {
            CbManagerState.UNAUTHORIZED ->
                ScanFailedException(
                    Macos.ERROR_UNAUTHORIZED,
                    "Bluetooth access is not authorized for this process",
                )
            CbManagerState.UNSUPPORTED ->
                ScanFailedException(
                    Macos.ERROR_UNSUPPORTED,
                    "This Mac does not support Bluetooth LE",
                )
            else -> ScanFailedException(Macos.ERROR_ADAPTER_OFF, "Bluetooth is not powered on")
        }

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds
    }
}

internal fun MacosEvent.Discovered.toScanRecord(): ScanRecord {
    val parsed = AdvertisingData.parse(advertising)
    return ScanRecord(
        handle = identifier,
        identifier = Identifier(identifier),
        name = parsed.localName ?: peripheralName,
        rssi = rssi,
        txPower = parsed.txPower,
        isConnectable = connectable ?: true,
        serviceUuids = parsed.serviceUuids,
        manufacturerData = parsed.manufacturerData,
        serviceData = parsed.serviceData,
        rawAdvertising = advertising.takeIf { it.isNotEmpty() },
    )
}
