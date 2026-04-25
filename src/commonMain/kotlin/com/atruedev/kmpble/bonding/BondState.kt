package com.atruedev.kmpble.bonding

/**
 * Bond (pairing) state of a peripheral.
 *
 * Observe via [com.atruedev.kmpble.peripheral.Peripheral.bondState].
 * On iOS, CoreBluetooth does not expose bond state, so [Unknown] is the typical value.
 */
public sealed interface BondState {
    /** No bond exists with this peripheral. */
    public data object NotBonded : BondState

    /** A bonding procedure is in progress. */
    public data object Bonding : BondState

    /** The peripheral is bonded - encryption keys are stored. */
    public data object Bonded : BondState

    /** iOS default - CoreBluetooth does not expose bond state directly. */
    public data object Unknown : BondState
}
