package com.atruedev.kmpble.peripheral

/**
 * Per-peripheral state.
 *
 * Tracks the connection state, bond state, and other metadata.
 */
internal class PeripheralState(
    val state: ConnectionOptions.State = ConnectionOptions.State.Disconnected,
    val bondState: BondManager.BondState = BondManager.BondState.NotBonded,
)
