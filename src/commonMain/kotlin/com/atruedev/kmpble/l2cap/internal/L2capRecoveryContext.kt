package com.atruedev.kmpble.l2cap.internal

import com.atruedev.kmpble.l2cap.L2capChannel

/**
 * Reopen parameters for client channels opened via [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel].
 */
internal class L2capRecoveryContext(
    val psm: Int,
    val secure: Boolean,
    val mtu: Int?,
    val reopen: suspend () -> L2capChannel,
)
