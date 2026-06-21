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
 * @property timeouts Per-operation timeout configuration. Use [OperationTimeouts.connect]
 *   to set the connection-establishment timeout.
 * @property transportType Preferred BLE transport.
 * @property phyMask Preferred PHY for the connection (BLE 5.0).
 * @property mtuRequest Request a specific MTU after connection, or `null` for the platform default.
 * @property bondingPreference Whether bonding should be required, optional, or skipped.
 * @property reconnectionStrategy Strategy to apply on unexpected disconnects.
 * @property gattOperationTimeout Maximum time a single GATT operation may take before the watchdog
 *   cancels it. Increase for devices with slow BLE stacks or firmware update flows.
 * @property timeouts Per-operation timeout configuration. Individual timeouts override
 *   the coarse-grained [gattOperationTimeout] when set. Use [OperationTimeouts]
 *   for fine-grained control over connect, discovery, read, write, MTU, and L2CAP operations.
 */
public data class ConnectionOptions(
    val autoConnect: Boolean = false,
    val timeouts: OperationTimeouts = OperationTimeouts(),
    val transportType: TransportType = TransportType.Auto,
    val phyMask: PhyMask = PhyMask.LE_1M,
    val mtuRequest: Int? = null,
    val bondingPreference: BondingPreference = BondingPreference.IfRequired,
    val reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.None,
    val gattOperationTimeout: Duration = 10.seconds,
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
    /**
     * Retry policy for GATT operations (read, write, MTU) that fail
     * with transient errors. Defaults to [RetryPolicy.NONE] (no retry,
     * backward compatible). Use [RetryPolicy.DEFAULT] for 3 attempts
     * with exponential backoff, or [RetryPolicy.AGGRESSIVE] for 5 attempts.
     */
    val gattRetryPolicy: RetryPolicy = RetryPolicy.NONE,
) {
    init {
        require(gattOperationTimeout.isPositive() && gattOperationTimeout.isFinite()) {
            "gattOperationTimeout must be positive and finite, was $gattOperationTimeout"
        }
    }

    /**
     * Validate this configuration and return a list of advisory warnings.
     *
     * Warnings highlight common misconfigurations that are technically
     * valid but may lead to unexpected behavior, poor performance, or
     * battery drain. Connections proceed regardless -- warnings are
     * informational only.
     *
     * ```kotlin
     * val options = ConnectionOptions(autoConnect = true, mtuRequest = 600)
     * val warnings = options.validate()
     * warnings.forEach { log.warn(it) }
     * ```
     */
    public fun validate(): List<ValidationWarning> =
        buildList {
            // MTU bounds
            if (mtuRequest != null && mtuRequest < 23) {
                add(ValidationWarning.MtuTooLow(mtuRequest))
            }
            if (mtuRequest != null && mtuRequest > 517) {
                add(ValidationWarning.MtuTooHigh(mtuRequest))
            }

            // Auto-connect battery drain
            if (autoConnect) {
                add(ValidationWarning.AutoConnectBattery)
            }

            // BR/EDR transport ignores PHY masks
            if (transportType == TransportType.BrEdr && phyMask != PhyMask.LE_1M) {
                add(ValidationWarning.PhyBrEdrMismatch(transportType, phyMask))
            }

            // Coded PHY with high MTU request
            if (phyMask == PhyMask.LE_CODED && mtuRequest != null && mtuRequest > 100) {
                add(ValidationWarning.CodedPhyHighMtu(mtuRequest))
            }

            // Required bonding without a pairing handler
            if (bondingPreference == BondingPreference.Required && pairingHandler == null) {
                add(ValidationWarning.RequiredBondingNoHandler)
            }
        }

    public companion object {
        /**
         * Balanced preset: 1M+2M PHY, 30s timeout, no auto-connect.
         * Suitable for most BLE peripherals within typical range.
         */
        public val Balanced: ConnectionOptions =
            ConnectionOptions(
                autoConnect = false,
                timeouts = OperationTimeouts(connect = 30.seconds),
                transportType = TransportType.LE,
                phyMask = PhyMask.LE_2M,
            )

        /**
         * Long-range preset: Coded PHY, 60s timeout for BLE 5.0 long-range scanning.
         * Use for peripherals beyond typical BLE range (100m+).
         */
        public val LongRange: ConnectionOptions =
            ConnectionOptions(
                autoConnect = false,
                timeouts = OperationTimeouts(connect = 60.seconds),
                transportType = TransportType.LE,
                phyMask = PhyMask.LE_CODED,
            )

        /**
         * Low-latency preset: 2M PHY, 10s timeout for high-throughput operations.
         * Use for firmware updates or fast data transfers where speed matters most.
         */
        public val LowLatency: ConnectionOptions =
            ConnectionOptions(
                autoConnect = false,
                timeouts = OperationTimeouts(connect = 10.seconds),
                transportType = TransportType.LE,
                phyMask = PhyMask.LE_2M,
                mtuRequest = 512,
            )
    }
}

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
    /** 1 Mbps PHY - universally supported. */
    LE_1M(1),

    /** 2 Mbps PHY - double throughput, shorter range. */
    LE_2M(2),

    /** Coded PHY (Long Range) - lower throughput, extended range. */
    LE_CODED(4),
}
