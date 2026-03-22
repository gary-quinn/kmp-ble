package com.atruedev.kmpble.server

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.Phy
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * BLE 5.0 extended advertiser — supports larger payloads, PHY selection,
 * and multiple concurrent advertising sets.
 *
 * ## Platform Support
 *
 * - **Android**: Full support via `AdvertisingSet` API (API 26+).
 * - **iOS**: CoreBluetooth does not expose extended advertising parameters.
 *   Falls back to legacy advertising via [CBPeripheralManager].
 *
 * ## Differences from [Advertiser]
 *
 * | Feature | Legacy ([Advertiser]) | Extended ([ExtendedAdvertiser]) |
 * |---------|----------------------|-------------------------------|
 * | Payload | ≤ 31 bytes | ≤ 254 bytes |
 * | PHY | LE 1M only | LE 1M, LE 2M, LE Coded |
 * | Concurrent sets | 1 | Multiple (hardware-dependent) |
 * | Periodic advertising | No | Yes |
 */
@ExperimentalBleApi
public interface ExtendedAdvertiser : AutoCloseable {
    /** Active advertising set IDs. */
    public val activeSets: StateFlow<Set<Int>>

    /**
     * Start an extended advertising set.
     *
     * @return advertising set ID for later reference
     * @throws AdvertiserException.StartFailed if the set cannot be started
     * @throws AdvertiserException.NotSupported if extended advertising is unavailable
     */
    public suspend fun startAdvertisingSet(config: ExtendedAdvertiseConfig): Int

    /**
     * Stop a specific advertising set by ID.
     *
     * Safe to call with an inactive set ID.
     */
    public suspend fun stopAdvertisingSet(setId: Int)

    /** Stop all advertising sets and release resources. */
    override fun close()
}

/**
 * Configuration for a BLE 5.0 extended advertising set.
 */
@ExperimentalBleApi
public class ExtendedAdvertiseConfig(
    public val name: String? = null,
    public val serviceUuids: List<Uuid> = emptyList(),
    public val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    public val serviceData: Map<Uuid, ByteArray> = emptyMap(),
    public val connectable: Boolean = true,
    public val scannable: Boolean = false,
    public val includeTxPower: Boolean = false,
    public val primaryPhy: Phy = Phy.Le1M,
    public val secondaryPhy: Phy = Phy.Le1M,
    public val interval: AdvertiseInterval = AdvertiseInterval.Balanced,
    public val txPower: AdvertiseTxPower = AdvertiseTxPower.Medium,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ExtendedAdvertiseConfig
        return name == other.name &&
            serviceUuids == other.serviceUuids &&
            connectable == other.connectable &&
            scannable == other.scannable &&
            includeTxPower == other.includeTxPower &&
            primaryPhy == other.primaryPhy &&
            secondaryPhy == other.secondaryPhy &&
            interval == other.interval &&
            txPower == other.txPower &&
            manufacturerData.keys == other.manufacturerData.keys &&
            manufacturerData.all { (k, v) -> other.manufacturerData[k]?.contentEquals(v) == true } &&
            serviceData.keys == other.serviceData.keys &&
            serviceData.all { (k, v) -> other.serviceData[k]?.contentEquals(v) == true }
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result +
            manufacturerData.entries.fold(0) { acc, (k, v) ->
                acc + k.hashCode() + v.contentHashCode()
            }
        result = 31 * result +
            serviceData.entries.fold(0) { acc, (k, v) ->
                acc + k.hashCode() + v.contentHashCode()
            }
        result = 31 * result + connectable.hashCode()
        result = 31 * result + scannable.hashCode()
        result = 31 * result + includeTxPower.hashCode()
        result = 31 * result + primaryPhy.hashCode()
        result = 31 * result + secondaryPhy.hashCode()
        result = 31 * result + interval.hashCode()
        result = 31 * result + txPower.hashCode()
        return result
    }

    override fun toString(): String =
        "ExtendedAdvertiseConfig(name=$name, serviceUuids=$serviceUuids, " +
            "connectable=$connectable, primaryPhy=$primaryPhy, secondaryPhy=$secondaryPhy)"
}

/**
 * Advertising interval controlling discovery speed vs power consumption.
 *
 * Maps to platform-specific interval ranges. Exact intervals are
 * hardware-dependent.
 */
@ExperimentalBleApi
public enum class AdvertiseInterval {
    /** ~1000ms interval. Lowest power, slowest discovery. */
    LowPower,

    /** ~250ms interval. Good default. */
    Balanced,

    /** ~100ms interval. Fastest discovery, highest power. */
    LowLatency,
}
