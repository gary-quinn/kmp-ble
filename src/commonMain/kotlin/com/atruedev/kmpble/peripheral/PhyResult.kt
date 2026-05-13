package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.Phy

/**
 * Result of a [Peripheral.setPreferredPhy] request.
 *
 * The negotiated [tx] / [rx] PHYs may differ from what was requested if the
 * controller or peer device does not support the preferred PHY.
 */
public data class PhyResult(
    val tx: Phy,
    val rx: Phy,
)
