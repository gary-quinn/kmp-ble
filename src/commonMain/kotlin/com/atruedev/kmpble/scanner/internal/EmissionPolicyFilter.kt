package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ScanEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.uuid.ExperimentalUuidApi

/**
 * Applies [EmissionPolicy] deduplication to a scan event flow.
 * Only [ScanEvent.Found] events are deduplicated; [ScanEvent.Failed] events pass through unchanged.
 */
internal fun Flow<ScanEvent>.applyEmissionPolicy(policy: EmissionPolicy): Flow<ScanEvent> =
    when (policy) {
        is EmissionPolicy.All -> this
        is EmissionPolicy.FirstThenChanges -> firstThenChanges(policy.rssiThreshold)
    }

@OptIn(ExperimentalUuidApi::class)
private fun Flow<ScanEvent>.firstThenChanges(rssiThreshold: Int): Flow<ScanEvent> =
    flow {
        val seen = mutableMapOf<Identifier, AdvertisementSnapshot>()
        collect { event ->
            when (event) {
                is ScanEvent.Found -> {
                    val ad = event.advertisement
                    val previous = seen[ad.identifier]
                    if (previous == null || hasChanged(previous, ad, rssiThreshold)) {
                        seen[ad.identifier] = ad.snapshot()
                        emit(event)
                    }
                }
                is ScanEvent.Failed -> emit(event)
            }
        }
    }

/**
 * Lightweight snapshot for change detection. Uses BleData.hashCode() which is
 * content-based - no ByteArray allocation needed for comparison.
 */
@OptIn(ExperimentalUuidApi::class)
private data class AdvertisementSnapshot(
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<kotlin.uuid.Uuid>,
    val manufacturerDataHash: Int,
    val serviceDataHash: Int,
)

@OptIn(ExperimentalUuidApi::class)
private fun Advertisement.snapshot() =
    AdvertisementSnapshot(
        name = name,
        rssi = rssi,
        serviceUuids = serviceUuids,
        manufacturerDataHash = manufacturerData.hashCode(),
        serviceDataHash = serviceData.hashCode(),
    )

@OptIn(ExperimentalUuidApi::class)
private fun hasChanged(
    previous: AdvertisementSnapshot,
    current: Advertisement,
    rssiThreshold: Int,
): Boolean {
    if (previous.name != current.name) return true
    if (previous.serviceUuids != current.serviceUuids) return true
    if (abs(previous.rssi - current.rssi) > rssiThreshold) return true
    val currentSnapshot = current.snapshot()
    if (previous.manufacturerDataHash != currentSnapshot.manufacturerDataHash) return true
    if (previous.serviceDataHash != currentSnapshot.serviceDataHash) return true
    return false
}
