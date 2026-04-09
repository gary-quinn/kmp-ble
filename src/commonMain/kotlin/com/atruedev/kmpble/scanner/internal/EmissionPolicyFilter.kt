package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.uuid.ExperimentalUuidApi

/**
 * Applies [EmissionPolicy] deduplication to an advertisement flow.
 */
internal fun Flow<Advertisement>.applyEmissionPolicy(policy: EmissionPolicy): Flow<Advertisement> =
    when (policy) {
        is EmissionPolicy.All -> this
        is EmissionPolicy.FirstThenChanges -> firstThenChanges(policy.rssiThreshold)
    }

@OptIn(ExperimentalUuidApi::class)
private fun Flow<Advertisement>.firstThenChanges(rssiThreshold: Int): Flow<Advertisement> =
    flow {
        val seen = mutableMapOf<Identifier, AdvertisementSnapshot>()
        collect { ad ->
            val previous = seen[ad.identifier]
            if (previous == null || hasChanged(previous, ad, rssiThreshold)) {
                seen[ad.identifier] = ad.snapshot()
                emit(ad)
            }
        }
    }

/**
 * Lightweight snapshot for change detection. Uses BleData.hashCode() which is
 * content-based — no ByteArray allocation needed for comparison.
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
    if (previous.manufacturerDataHash != current.manufacturerData.hashCode()) return true
    if (previous.serviceDataHash != current.serviceData.hashCode()) return true
    return false
}
