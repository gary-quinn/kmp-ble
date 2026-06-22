package com.atruedev.kmpble.periodic

import com.atruedev.kmpble.connection.Phy

/**
 * A single periodic advertising report received from a synced advertiser.
 *
 * Periodic advertising reports are received on secondary advertising channels
 * at a fixed interval after sync is established. Unlike scan results, these
 * arrive without continuous scanning and carry the periodic advertising data
 * set by the advertiser's [com.atruedev.kmpble.server.PeriodicAdvertisingParameters].
 */
public data class PeriodicReport(
    /** RSSI of the periodic advertising packet in dBm. */
    public val rssi: Int,
    /** TX power level in dBm, if included by the advertiser. */
    public val txPower: Int?,
    /** Advertising set ID identifying the periodic advertising set. */
    public val advertisingSid: Int,
    /** Raw periodic advertising data payload. */
    public val data: ByteArray,
    /** PHY on which this report was received. */
    public val phy: Phy,
    /** Status indicating whether the data is complete or truncated. */
    public val dataStatus: DataStatus,
) {
    /**
     * Whether the periodic advertising data is complete or truncated.
     */
    public enum class DataStatus {
        /** Data is complete. */
        Complete,

        /** Data was truncated -- the full payload exceeds the report size. */
        Truncated,
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as PeriodicReport
        return rssi == other.rssi &&
            txPower == other.txPower &&
            advertisingSid == other.advertisingSid &&
            data.contentEquals(other.data) &&
            phy == other.phy &&
            dataStatus == other.dataStatus
    }

    override fun hashCode(): Int {
        var result = rssi
        result = 31 * result + (txPower ?: 0)
        result = 31 * result + advertisingSid
        result = 31 * result + data.contentHashCode()
        result = 31 * result + phy.hashCode()
        result = 31 * result + dataStatus.hashCode()
        return result
    }

    override fun toString(): String =
        "PeriodicReport(rssi=$rssi, txPower=$txPower, sid=$advertisingSid, " +
            "data=${data.size}B, phy=$phy, dataStatus=$dataStatus)"
}
