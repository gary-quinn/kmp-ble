package com.atruedev.kmpble.connection

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.bonding.PairingHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Options for establishing a connection to a peripheral.
 *
 * Pass to [com.atruedev.kmpble.peripheral.Peripheral.connect] or use a
 * [ConnectionRecipe] preset for common use cases.
 *
 * @property autoConnect If `true`, the system connects when the peripheral is in range (Android only).
 * @property timeout Maximum duration to wait for a connection before timing out.
 * @property transportType Preferred BLE transport.
 * @property phyMask Preferred PHY for the connection (BLE 5.0).
 * @property mtuRequest Request a specific MTU after connection, or `null` for the platform default.
 * @property bondingPreference Whether bonding should be required, optional, or skipped.
 * @property reconnectionStrategy Strategy to apply on unexpected disconnects.
 */
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

/** Whether bonding should be initiated during connection. */
public enum class BondingPreference {
    /** Skip bonding entirely. */
    None,

    /** Bond only if the peripheral requires it. */
    IfRequired,

    /** Always initiate bonding. Fail if bonding is rejected. */
    Required,
}

/** Preferred Bluetooth transport type. */
public enum class TransportType {
    /** Let the system choose the transport. */
    Auto,

    /** Bluetooth Low Energy transport. */
    LE,

    /** Classic Bluetooth (BR/EDR) transport. */
    BrEdr,
}

/** Physical layer (PHY) preference for BLE 5.0 connections. */
public enum class PhyMask(
    public val value: Int,
) {
    /** 1 Mbps PHY — universally supported. */
    LE_1M(1),

    /** 2 Mbps PHY — double throughput, shorter range. */
    LE_2M(2),

    /** Coded PHY (Long Range) — lower throughput, extended range. */
    LE_CODED(4),
}
