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
    public fun startAdvertising(config: AdvertiseConfig)

    /**
     * Stop BLE advertising.
     *
     * Safe to call when not advertising.
     */
    public fun stopAdvertising()

    /**
     * Stop advertising and release resources.
     *
     * Safe to call multiple times.
     */
    override fun close()
}

public data class AdvertiseConfig(
    /** Device name included in advertisement. Null = use system device name. */
    val name: String? = null,
    /** Service UUIDs to advertise. */
    val serviceUuids: List<Uuid> = emptyList(),
    /** Manufacturer-specific data keyed by company ID. */
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    /** Whether the advertisement is connectable. */
    val connectable: Boolean = true,
    /** Include TX power level in advertisement. */
    val includeTxPower: Boolean = false,
)

public expect fun Advertiser(): Advertiser
