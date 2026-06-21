package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks bond state for an iOS CBPeripheral.
 *
 * Unlike Android, iOS does not expose explicit bond state APIs.
 * CoreBluetooth manages pairing/bonding transparently - the OS
 * presents a pairing dialog when the peripheral requires encryption.
 *
 * This implementation provides the bond state contract required by
 * [com.atruedev.kmpble.peripheral.Peripheral] while acknowledging
 * the iOS platform limitations.
 *
 * All mutable state is confined to [PeripheralContext.scope] via
 * `limitedParallelism(1)` - no synchronization primitives needed.
 */
internal class IosBondManager(
    private val peripheralContext: PeripheralContext,
) {
    internal val bondState: StateFlow<BondState> get() = peripheralContext.bondState

    /**
     * Start bond state tracking.
     *
     * On iOS, bonding is managed transparently by the OS.
     * Bond state remains [BondState.Unknown] unless the peripheral
     * requires encryption (detected via GATT auth error handling).
     */
    internal fun start() {
        // iOS manages bonding transparently during GATT operations
    }

    /**
     * Stop bond state tracking.
     *
     * No broadcast receivers or OS resources to clean up on iOS.
     */
    internal fun stop() {
        // No resources to release on iOS
    }

    /**
     * Create a bond with the peripheral.
     *
     * On iOS, bonding is initiated implicitly by the OS when the peripheral
     * requires encryption. This method returns `true` to indicate the OS
     * will handle bonding if needed during subsequent GATT operations.
     *
     * @return `true` always - iOS handles bonding transparently
     */
    internal suspend fun createBond(): Boolean {
        peripheralContext.updateBondState(BondState.Unknown)
        return true
    }

    /**
     * Remove the bond with the peripheral.
     *
     * iOS does not support programmatic bond removal. Users must remove
     * bonds from Settings > Bluetooth.
     */
    @ExperimentalBleApi
    internal fun removeBond(): BondRemovalResult =
        BondRemovalResult.NotSupported(
            "iOS does not support programmatic bond removal. Remove from Settings > Bluetooth.",
        )
}
