package com.atruedev.kmpble.connection

/**
 * Validation warnings returned by [ConnectionOptions.validate].
 *
 * Warnings highlight configuration choices that are technically valid
 * but commonly lead to unexpected behavior, poor performance, or
 * battery drain. They are advisory only -- connections proceed
 * regardless.
 */
public sealed class ValidationWarning {
    /**
     * [ConnectionOptions.mtuRequest] is below the BLE minimum
     * ATT MTU of 23 bytes. Many peripherals will silently ignore
     * an MTU below 23.
     */
    public data class MtuTooLow(
        val requested: Int,
    ) : ValidationWarning()

    /**
     * [ConnectionOptions.mtuRequest] exceeds the practical maximum
     * ATT MTU of 517 bytes (23 base + 247 data length extension).
     * Values above 517 are rejected by most BLE stacks.
     */
    public data class MtuTooHigh(
        val requested: Int,
    ) : ValidationWarning()

    /**
     * [ConnectionOptions.autoConnect] is `true`, which keeps the
     * radio active and drains battery. Prefer manual connection
     * unless background reconnection is explicitly required.
     */
    public data object AutoConnectBattery : ValidationWarning()

    /**
     * [ConnectionOptions.phyMask] is set to a LE-specific PHY but
     * [ConnectionOptions.transportType] is [TransportType.BrEdr].
     * PHY masks apply to LE connections only; BR/EDR ignores them.
     */
    public data class PhyBrEdrMismatch(
        val transportType: TransportType,
        val phyMask: PhyMask,
    ) : ValidationWarning()

    /**
     * [ConnectionOptions.phyMask] is [PhyMask.LE_CODED] while
     * [ConnectionOptions.mtuRequest] requests a high MTU. Coded
     * PHY has significantly lower throughput; a high MTU request
     * may not be honored by the controller.
     */
    public data class CodedPhyHighMtu(
        val mtuRequest: Int,
    ) : ValidationWarning()

    /**
     * [ConnectionOptions.bondingPreference] is [BondingPreference.Required]
     * but [ConnectionOptions.pairingHandler] is `null`. Without a
     * pairing handler, passkey and numeric comparison pairing requests
     * rely solely on the system dialog, which may be suppressed or
     * time out on some platforms.
     */
    public data object RequiredBondingNoHandler : ValidationWarning()
}
