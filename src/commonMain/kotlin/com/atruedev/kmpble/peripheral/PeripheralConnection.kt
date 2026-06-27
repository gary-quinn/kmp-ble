package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection lifecycle management for a BLE peripheral.
 *
 * Covers the full peripheral state machine: connect → connected → disconnect → closed.
 * Also manages bond state and provides lifecycle cleanup via [close].
 *
 * @see PeripheralDiscovery for service/characteristic discovery
 * @see PeripheralGatt for GATT read/write operations
 * @see PeripheralInfo for connection quality and MTU negotiation
 */
public interface PeripheralConnection : AutoCloseable {
    /**
     * Unique identifier for this peripheral (e.g., device address on Android, UUID on iOS).
     */
    public val identifier: Identifier

    // --- Connection Lifecycle ---

    /**
     * Establish a connection to this peripheral with the specified [options].
     *
     * May be called multiple times - if already connected, this is a no-op.
     * The connection state is exposed via [state].
     *
     * @throws com.atruedev.kmpble.error.BleException if connection fails
     * @throws com.atruedev.kmpble.error.BleException if connection times out
     */
    public suspend fun connect(options: ConnectionOptions = ConnectionOptions())

    /**
     * Disconnect from this peripheral and release GATT resources.
     *
     * Does NOT call [close] - the peripheral can be reconnected via [connect].
     * Observations are preserved and will be re-subscribed on reconnect.
     *
     * @throws com.atruedev.kmpble.error.BleException if disconnect fails
     */
    public suspend fun disconnect()

    /**
     * Permanently close this peripheral and release all resources.
     *
     * This is a final operation - the peripheral cannot be reconnected after close.
     * Stops all observations, releases the GATT connection, and removes from the
     * registry. Always call when done using this peripheral to avoid resource leaks.
     */
    override fun close()

    // --- State ---

    /**
     * Reactive state flow of the connection lifecycle.
     *
     * Emits state changes: [State.Disconnected] → [State.Connecting] → [State.Connected] → [State.Disconnecting] → [State.Disconnected]
     * State transitions are monotonic - never goes back to [State.Disconnected] after [State.Connected].
     *
     * For observation subscriptions that survive disconnects, use [PeripheralGatt.observe].
     */
    public val state: StateFlow<State>

    /**
     * Reactive bond state flow for bonded peripherals.
     *
     * Only meaningful on platforms that support bonding (Android, iOS).
     * Windows typically does not expose bonding state.
     *
     * Emits: [BondState.NotBonded] → [BondState.Bonding] → [BondState.Bonding] → [BondState.Bonded]
     * May also emit [BondState.Bonding] → [BondState.NotBonded] if pairing is rejected.
     */
    public val bondState: StateFlow<BondState>

    // --- Bond Management ---

    /**
     * Remove the bond for this peripheral.
     *
     * Requires platform-specific permissions and may trigger re-pairing on next connection.
     * Only works on bonded peripherals - returns [BondRemovalResult.NotBonded] if not bonded.
     *
     * @throws com.atruedev.kmpble.error.BleException if bond removal fails
     * @see BondState.Bonded
     */
    @ExperimentalBleApi
    public fun removeBond(): BondRemovalResult
}
