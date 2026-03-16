package com.atruedev.kmpble.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class ConnectionOptions(
    val autoConnect: Boolean = false,
    val timeout: Duration = 30.seconds,
    val transportType: TransportType = TransportType.Auto,
    val phyMask: PhyMask = PhyMask.LE_1M,
    val mtuRequest: Int? = null,
    val bondingPreference: BondingPreference = BondingPreference.IfRequired,
    val reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.None,
)

public enum class BondingPreference {
    None,
    IfRequired,
    Required,
}

public enum class TransportType {
    Auto,
    LE,
    BrEdr,
}

public enum class PhyMask(public val value: Int) {
    LE_1M(1),
    LE_2M(2),
    LE_CODED(4),
}
