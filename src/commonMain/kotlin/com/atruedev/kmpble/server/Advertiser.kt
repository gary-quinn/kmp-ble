package com.atruedev.kmpble.server

import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * BLE advertiser — broadcasts the device's presence so centrals can discover it.
 *
 * ## Usage
 *
 * ```kotlin
 * val advertiser = Advertiser()
 *
 * advertiser.startAdvertising(AdvertiseConfig(
 *     name = "MyDevice",
 *     serviceUuids = listOf(myServiceUuid),
 *     connectable = true,
 * ))
 *
 * // Later...
 * advertiser.stopAdvertising()
 * advertiser.close()
 * ```
 *
 * ## Independence
 *
 * The advertiser is fully independent from [GattServer]:
 * - Advertise without a server = beacon (broadcast-only, no connections)
 * - Server without advertising = accept connections from known devices
 * - Both together = standard peripheral role
 */
public interface Advertiser : AutoCloseable {

    /**
     * Whether advertising is currently active.
     */
    public val isAdvertising: StateFlow<Boolean>

    /**
     * Start BLE advertising.
     *
     * @param config Advertising configuration
     * @throws AdvertiserException.StartFailed if advertising cannot start
     * @throws AdvertiserException.NotSupported if advertising not available
     * @throws AdvertiserException.AlreadyAdvertising if already advertising
     */
    public suspend fun startAdvertising(config: AdvertiseConfig)

    /**
     * Stop BLE advertising.
     *
     * Safe to call when not advertising.
     */
    public suspend fun stopAdvertising()

    /**
     * Stop advertising and release resources.
     *
     * Safe to call multiple times.
     */
    override fun close()
}

/**
 * Advertising mode controlling interval and power consumption.
 *
 * - [LowLatency]: ~100ms interval, highest power. Use for short bursts (pairing, discovery).
 * - [Balanced]: ~250ms interval, moderate power. Good default for most use cases.
 * - [LowPower]: ~1000ms interval, lowest power. Use for long-running background advertising.
 */
public enum class AdvertiseMode {
    LowPower,
    Balanced,
    LowLatency,
}

/**
 * Transmit power level for advertising.
 *
 * Higher power = longer range but more battery drain.
 *
 * - [UltraLow]: shortest range, minimal power.
 * - [Low]: short range.
 * - [Medium]: moderate range. Good default.
 * - [High]: longest range, highest power.
 */
public enum class AdvertiseTxPower {
    UltraLow,
    Low,
    Medium,
    High,
}

public class AdvertiseConfig(
    /** Device name included in advertisement. Null = use system device name. */
    public val name: String? = null,
    /** Service UUIDs to advertise. */
    public val serviceUuids: List<Uuid> = emptyList(),
    /** Manufacturer-specific data keyed by company ID. */
    public val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    /** Whether the advertisement is connectable. */
    public val connectable: Boolean = true,
    /** Include TX power level in advertisement. */
    public val includeTxPower: Boolean = false,
    /** Advertising mode controlling interval and power consumption. */
    public val mode: AdvertiseMode = AdvertiseMode.Balanced,
    /** Transmit power level. */
    public val txPower: AdvertiseTxPower = AdvertiseTxPower.Medium,
) {
    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AdvertiseConfig
        return name == other.name &&
            serviceUuids == other.serviceUuids &&
            connectable == other.connectable &&
            includeTxPower == other.includeTxPower &&
            mode == other.mode &&
            txPower == other.txPower &&
            manufacturerData.keys == other.manufacturerData.keys &&
            manufacturerData.all { (key, value) ->
                other.manufacturerData[key]?.contentEquals(value) == true
            }
    }

    public override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + manufacturerData.entries.fold(0) { acc, (key, value) ->
            acc + key.hashCode() + value.contentHashCode()
        }
        result = 31 * result + connectable.hashCode()
        result = 31 * result + includeTxPower.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + txPower.hashCode()
        return result
    }

    public override fun toString(): String =
        "AdvertiseConfig(name=$name, serviceUuids=$serviceUuids, connectable=$connectable, includeTxPower=$includeTxPower, mode=$mode, txPower=$txPower)"
}

public expect fun Advertiser(): Advertiser

/**
 * Create a platform-specific [ExtendedAdvertiser] for BLE 5.0 extended advertising.
 *
 * - Android: Uses `AdvertisingSet` API with full BLE 5.0 support.
 * - iOS: Falls back to legacy advertising via `CBPeripheralManager`.
 */
@com.atruedev.kmpble.ExperimentalBleApi
public expect fun ExtendedAdvertiser(): ExtendedAdvertiser
