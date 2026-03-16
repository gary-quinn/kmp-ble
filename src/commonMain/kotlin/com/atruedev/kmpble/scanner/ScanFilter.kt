package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.AndroidOnly
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Internal representation of a single filter predicate.
 * These are combined in AND groups (all must match), which are then OR'd together.
 */
@OptIn(ExperimentalUuidApi::class)
internal sealed interface ScanPredicate {
    data class Name(val exact: String) : ScanPredicate
    data class NamePrefix(val prefix: String) : ScanPredicate
    data class ServiceUuid(val uuid: Uuid) : ScanPredicate
    data class ServiceData(val uuid: Uuid, val data: ByteArray?, val mask: ByteArray?) : ScanPredicate {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ServiceData

            if (uuid != other.uuid) return false
            if (!data.contentEquals(other.data)) return false
            if (!mask.contentEquals(other.mask)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = uuid.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + (mask?.contentHashCode() ?: 0)
            return result
        }
    }

    data class ManufacturerData(val companyId: Int, val data: ByteArray?, val mask: ByteArray?) : ScanPredicate {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ManufacturerData

            if (companyId != other.companyId) return false
            if (!data.contentEquals(other.data)) return false
            if (!mask.contentEquals(other.mask)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = companyId
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + (mask?.contentHashCode() ?: 0)
            return result
        }
    }

    data class MinRssi(val minRssi: Int) : ScanPredicate
    data class Address(val mac: String) : ScanPredicate
}

/**
 * DSL scope for a single AND predicate group.
 * All conditions added within one [MatchScope] must match for the group to pass.
 */
@OptIn(ExperimentalUuidApi::class)
public class MatchScope internal constructor() {
    internal val predicates: MutableList<ScanPredicate> = mutableListOf()

    /** Match devices with this exact advertised name. */
    public fun name(exact: String) {
        predicates += ScanPredicate.Name(exact)
    }

    /** Match devices whose advertised name starts with [prefix]. */
    public fun namePrefix(prefix: String) {
        predicates += ScanPredicate.NamePrefix(prefix)
    }

    /** Match devices advertising this service UUID. Accepts 16-bit short form ("180d") or full UUID. */
    public fun serviceUuid(uuid: String) {
        predicates += ScanPredicate.ServiceUuid(uuidFrom(uuid))
    }

    /** Match devices advertising this service UUID. */
    public fun serviceUuid(uuid: Uuid) {
        predicates += ScanPredicate.ServiceUuid(uuid)
    }

    /** Match devices with specific service data. */
    public fun serviceData(uuid: String, data: ByteArray? = null, mask: ByteArray? = null) {
        predicates += ScanPredicate.ServiceData(uuidFrom(uuid), data, mask)
    }

    /** Match devices with specific manufacturer data. [companyId] is the 16-bit Bluetooth SIG company ID. */
    public fun manufacturerData(companyId: Int, data: ByteArray? = null, mask: ByteArray? = null) {
        predicates += ScanPredicate.ManufacturerData(companyId, data, mask)
    }

    /** Match devices with RSSI >= [minRssi]. Post-filter on both platforms. */
    public fun rssi(minRssi: Int) {
        predicates += ScanPredicate.MinRssi(minRssi)
    }

    /**
     * Match devices with this MAC address. **Android only.**
     * Apple does not expose MAC addresses — this filter has no effect on iOS.
     */
    @AndroidOnly
    public fun address(mac: String) {
        predicates += ScanPredicate.Address(mac)
    }
}

/**
 * DSL scope for filter groups. Each [match] block is an AND group.
 * Multiple [match] blocks are OR'd together.
 */
public class FiltersScope internal constructor() {
    internal val matchGroups: MutableList<List<ScanPredicate>> = mutableListOf()

    /** Add an AND predicate group. All conditions within must match. */
    public fun match(block: MatchScope.() -> Unit) {
        val scope = MatchScope().apply(block)
        if (scope.predicates.isNotEmpty()) {
            matchGroups += scope.predicates.toList()
        }
    }
}

/**
 * Converts a UUID string to [Uuid]. Accepts:
 * - 16-bit short form: "180d" → "0000180d-0000-1000-8000-00805f9b34fb"
 * - 32-bit short form: "0000180d" → "0000180d-0000-1000-8000-00805f9b34fb"
 * - Full 128-bit form: "0000180d-0000-1000-8000-00805f9b34fb"
 */
@OptIn(ExperimentalUuidApi::class)
public fun uuidFrom(shortOrFull: String): Uuid {
    val full = when (shortOrFull.length) {
        4 -> "0000${shortOrFull}-0000-1000-8000-00805f9b34fb"
        8 -> "${shortOrFull}-0000-1000-8000-00805f9b34fb"
        else -> shortOrFull
    }
    return Uuid.parse(full)
}
