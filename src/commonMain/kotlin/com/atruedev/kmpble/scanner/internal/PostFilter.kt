package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.ScanPredicate
import kotlin.uuid.ExperimentalUuidApi

/**
 * Evaluates OR-of-ANDs filter logic against an advertisement.
 *
 * @param filterGroups List of AND groups. Empty = match everything.
 * @return true if the advertisement matches at least one AND group (or filters are empty).
 */
@OptIn(ExperimentalUuidApi::class)
internal fun Advertisement.matchesFilters(filterGroups: List<List<ScanPredicate>>): Boolean {
    if (filterGroups.isEmpty()) return true
    return filterGroups.any { andGroup ->
        andGroup.all { predicate -> matchesPredicate(predicate) }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun Advertisement.matchesPredicate(predicate: ScanPredicate): Boolean =
    when (predicate) {
        is ScanPredicate.Name -> name == predicate.exact
        is ScanPredicate.NamePrefix -> name?.startsWith(predicate.prefix) == true
        is ScanPredicate.ServiceUuid -> predicate.uuid in serviceUuids
        is ScanPredicate.MinRssi -> rssi >= predicate.minRssi
        is ScanPredicate.Address -> identifier.value.equals(predicate.mac, ignoreCase = true)
        is ScanPredicate.ManufacturerData -> matchesManufacturerData(predicate)
        is ScanPredicate.ServiceData -> matchesServiceData(predicate)
    }

@OptIn(ExperimentalUuidApi::class)
private fun Advertisement.matchesManufacturerData(predicate: ScanPredicate.ManufacturerData): Boolean {
    val actual = manufacturerData[predicate.companyId] ?: return false
    val expected = predicate.data ?: return true // company ID match only
    val mask = predicate.mask
    return matchesWithMask(actual, expected, mask)
}

@OptIn(ExperimentalUuidApi::class)
private fun Advertisement.matchesServiceData(predicate: ScanPredicate.ServiceData): Boolean {
    val actual = serviceData[predicate.uuid] ?: return false
    val expected = predicate.data ?: return true // UUID match only
    val mask = predicate.mask
    return matchesWithMask(actual, expected, mask)
}

private fun matchesWithMask(
    actual: BleData,
    expected: ByteArray,
    mask: ByteArray?,
): Boolean {
    if (actual.size < expected.size) return false
    if (mask != null) {
        for (i in expected.indices) {
            val m = if (i < mask.size) mask[i] else 0xFF.toByte()
            if ((actual[i].toInt() and m.toInt()) != (expected[i].toInt() and m.toInt())) {
                return false
            }
        }
        return true
    }
    for (i in expected.indices) {
        if (actual[i] != expected[i]) return false
    }
    return true
}
