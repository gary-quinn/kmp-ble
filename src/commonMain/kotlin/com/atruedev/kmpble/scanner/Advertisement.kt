package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.Phy
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parsed BLE advertisement data from a single scan result.
 *
 * [manufacturerData] and [serviceData] values are [BleData] - zero-copy on iOS
 * (wraps NSData), lightweight on Android (wraps ByteArray). Call [BleData.toByteArray]
 * only when a mutable copy is needed (e.g., for protocol parsing in consumer code).
 *
 * ## BLE 5.0 Extended Advertising
 *
 * Extended advertisements support payloads > 31 bytes and additional PHY options.
 * On Android, set [ScannerConfig.legacyOnly] = `false` to receive extended ads.
 * On iOS, CoreBluetooth receives extended advertisements transparently.
 *
 * Check [isLegacy] to distinguish legacy from extended advertisements.
 *
 * ## Raw advertising payload
 *
 * [rawAdvertising] holds the on-air AD record when the platform exposes it, or
 * a faithful reconstruction otherwise. Always inspect the [RawAdvertising]
 * variant before using the bytes for byte-exact comparisons. Use the parsed
 * fields above for cross-platform code, [rawAdvertising] for diagnostics or
 * vendor-specific AD types the library does not parse.
 */
@OptIn(ExperimentalUuidApi::class)
public data class Advertisement(
    public val identifier: Identifier,
    public val name: String?,
    public val rssi: Int,
    public val txPower: Int?,
    public val isConnectable: Boolean,
    public val serviceUuids: List<Uuid>,
    public val manufacturerData: Map<Int, BleData>,
    public val serviceData: Map<Uuid, BleData>,
    public val timestampNanos: Long,
    public val isLegacy: Boolean = true,
    public val primaryPhy: Phy = Phy.Le1M,
    public val secondaryPhy: Phy? = null,
    public val advertisingSid: Int? = null,
    public val periodicAdvertisingInterval: Int? = null,
    public val dataStatus: DataStatus = DataStatus.Complete,
    public val rawAdvertising: RawAdvertising? = null,
) {
    internal var platformContext: Any? = null

    override fun toString(): String =
        "Advertisement(identifier=$identifier, name=$name, rssi=$rssi, " +
            "serviceUuids=$serviceUuids, isLegacy=$isLegacy, " +
            "rawAdvertising=${rawAdvertising?.describe()})"
}

private fun RawAdvertising.describe(): String =
    when (this) {
        is RawAdvertising.OnAir -> "OnAir(${bytes.size}B)"
        is RawAdvertising.Reconstructed -> "Reconstructed(${bytes.size}B)"
    }

/**
 * Whether the advertisement data is complete or truncated.
 *
 * Extended advertisements may arrive in fragments. [Truncated] indicates
 * the host controller received a partial payload.
 */
public enum class DataStatus {
    Complete,
    Truncated,
}
