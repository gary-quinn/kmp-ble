package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.backend.AdvertiserTransport
import com.atruedev.kmpble.backend.AdvertisingSetSpec
import com.atruedev.kmpble.backend.backendLog
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import com.atruedev.kmpble.server.AdvertiserException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `CBPeripheralManager` advertising. CoreBluetooth advertises only the local name and service
 * UUIDs, one set per process; other [AdvertisingSetSpec] fields are logged and ignored, as on iOS.
 */
internal class MacosAdvertiserTransport(
    private val stack: MacosStack,
    private val settleTimeout: Duration = SETTLE_TIMEOUT,
    private val startTimeout: Duration = START_TIMEOUT,
) : AdvertiserTransport {
    override val maxAdvertisingSets: Int = 1

    private val activeSet = AtomicInteger(NO_SET)

    override suspend fun start(
        setId: Int,
        spec: AdvertisingSetSpec,
    ) {
        val state =
            try {
                stack.awaitPeripheralManagerSettled(settleTimeout)
            } catch (e: MissingUsageDescriptionException) {
                throw AdvertiserException.StartFailed(e.message ?: "", e)
            } catch (e: UnsatisfiedLinkError) {
                throw AdvertiserException.NotSupported(e.message ?: "kmp-ble-macos native library unavailable")
            }
        if (state != CbManagerState.POWERED_ON) throw AdvertiserException.StartFailed("Bluetooth is not powered on")
        if (!stack.advertisingOwner.compareAndSet(null, this)) {
            throw AdvertiserException.StartFailed(
                "CoreBluetooth advertises one set at a time and another advertiser is active",
            )
        }
        warnUnsupported(spec)
        val started = CompletableDeferred<MacosEvent.PmAdvertisingStarted>()
        val registration =
            stack.addPeripheralManagerListener { event ->
                if (event is MacosEvent.PmAdvertisingStarted) started.complete(event)
            }
        try {
            stack.api.pmStartAdvertising(spec.name, spec.serviceUuids.map { it.toString() })
            val result =
                withTimeoutOrNull(startTimeout) { started.await() }
                    ?: throw AdvertiserException.StartFailed(
                        "CoreBluetooth did not confirm advertising within $startTimeout",
                    )
            if (result.status != 0) throw AdvertiserException.StartFailed(result.message ?: "status ${result.status}")
            activeSet.set(setId)
        } catch (e: Throwable) {
            stack.api.pmStopAdvertising()
            stack.advertisingOwner.compareAndSet(this, null)
            throw e
        } finally {
            registration.close()
        }
    }

    override suspend fun stop(setId: Int) {
        if (!activeSet.compareAndSet(setId, NO_SET)) return
        stack.api.pmStopAdvertising()
        stack.advertisingOwner.compareAndSet(this, null)
    }

    override fun close() {
        if (activeSet.getAndSet(NO_SET) != NO_SET) stack.api.pmStopAdvertising()
        stack.advertisingOwner.compareAndSet(this, null)
    }

    private fun warnUnsupported(spec: AdvertisingSetSpec) {
        val ignored =
            buildList {
                if (spec.manufacturerData.isNotEmpty()) add("manufacturerData")
                if (spec.serviceData.isNotEmpty()) add("serviceData")
                if (spec.includeTxPower) add("includeTxPower")
                if (spec.mode != AdvertiseMode.Balanced) add("mode")
                if (spec.txPower != AdvertiseTxPower.Medium) add("txPower")
                if (!spec.connectable) add("connectable=false")
                if (!spec.legacy) add("extended advertising")
            }
        if (ignored.isEmpty()) return
        backendLog(
            BleLogEvent.Warning(
                identifier = null,
                message = "CoreBluetooth advertising ignores ${ignored.joinToString()} on macOS",
            ),
        )
    }

    private companion object {
        const val NO_SET = -1
        val SETTLE_TIMEOUT = 10.seconds
        val START_TIMEOUT = 10.seconds
    }
}
