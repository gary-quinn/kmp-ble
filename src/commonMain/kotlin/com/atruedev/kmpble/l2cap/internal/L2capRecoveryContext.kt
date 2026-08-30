package com.atruedev.kmpble.l2cap.internal

import com.atruedev.kmpble.l2cap.L2capChannel

/**
 * Reopen hook for client channels opened via [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel].
 */
internal class L2capRecoveryContext(
    val reopen: suspend () -> L2capChannel,
)
