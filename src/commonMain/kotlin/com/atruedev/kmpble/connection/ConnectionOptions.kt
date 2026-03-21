package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.bonding.PairingHandler
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
    /**
     * Handler for pairing events that require user interaction.
     *
     * When set, the library routes pairing requests (numeric comparison,
     * passkey entry, OOB) through this handler instead of relying solely
     * on the system dialog.
     *
     * On iOS, the system dialog is always shown. The handler receives
     * events for observability but cannot suppress the dialog.
     */
    @property:ExperimentalBleApi
    val pairingHandler: PairingHandler? = null,
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
