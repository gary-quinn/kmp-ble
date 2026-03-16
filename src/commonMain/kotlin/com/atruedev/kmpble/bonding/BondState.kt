package com.atruedev.kmpble.bonding

public sealed interface BondState {
    public data object NotBonded : BondState
    public data object Bonding : BondState
    public data object Bonded : BondState
    /** iOS default — CoreBluetooth does not expose bond state directly. */
    public data object Unknown : BondState
}
