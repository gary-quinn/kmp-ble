package com.atruedev.kmpble.connection

/**
 * BLE physical layer (PHY) type.
 *
 * - [Le1M]: 1 Mbps, universal support (BLE 4.0+)
 * - [Le2M]: 2 Mbps, higher throughput (BLE 5.0+)
 * - [LeCoded]: Long range with forward error correction (BLE 5.0+)
 */
public enum class Phy {
    Le1M,
    Le2M,
    LeCoded,
}
