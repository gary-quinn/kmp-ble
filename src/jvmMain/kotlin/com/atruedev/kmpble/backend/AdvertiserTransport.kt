package com.atruedev.kmpble.backend

import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** LE advertising for one backend. Each set id is started at most once until stopped. */
@KmpBleBackendApi
public interface AdvertiserTransport : AutoCloseable {
    /** Maximum concurrently active sets. `1` means only legacy single-set advertising. */
    public val maxAdvertisingSets: Int

    /** Fails with [com.atruedev.kmpble.server.AdvertiserException.StartFailed]. */
    public suspend fun start(
        setId: Int,
        spec: AdvertisingSetSpec,
    )

    public suspend fun stop(setId: Int)

    /** Stops every set this transport started. Non-blocking and idempotent. */
    override fun close()
}

/**
 * @property legacy `true` for legacy PDUs; `false` requests extended advertising on
 *   [secondaryPhy] where the backend supports it.
 */
@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class AdvertisingSetSpec(
    public val name: String?,
    public val serviceUuids: List<Uuid>,
    public val manufacturerData: Map<Int, ByteArray>,
    public val serviceData: Map<Uuid, ByteArray>,
    public val connectable: Boolean,
    public val scannable: Boolean,
    public val includeTxPower: Boolean,
    public val mode: AdvertiseMode,
    public val txPower: AdvertiseTxPower,
    public val legacy: Boolean,
    public val primaryPhy: Phy,
    public val secondaryPhy: Phy?,
)
