package com.atruedev.kmpble.l2cap

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Server-side L2CAP CoC listener.
 *
 * Counterpart to [com.atruedev.kmpble.peripheral.Peripheral.openL2capChannel]
 * (client side). [open] publishes the listener; the OS assigns a PSM that
 * centrals discover (typically by reading a custom GATT characteristic) and
 * use to open channels. Each accepted channel is emitted through [incoming].
 *
 * ## Usage
 *
 * ```kotlin
 * val listener = L2capListener()
 * listener.open(secure = true)
 * val psm = listener.psm
 *
 * // Expose PSM through a GATT characteristic so centrals can discover it
 * gattServer.notify(PSM_CHAR_UUID, null, BleData(psm.toUInt16LeBytes()))
 *
 * listener.incoming.collect { channel ->
 *     scope.launch {
 *         channel.incoming.collect { bytes -> handle(bytes) }
 *     }
 * }
 *
 * listener.close()
 * ```
 *
 * ## Independence from GattServer
 *
 * The listener is conceptually independent from [com.atruedev.kmpble.server.GattServer]:
 * a peripheral may publish L2CAP without GATT, or vice versa. On iOS the
 * underlying `CBPeripheralManager` is shared with `GattServer` and `Advertiser`
 * out of platform necessity, but the surface API does not require either to
 * be active.
 *
 * ## Lifecycle
 *
 * - Created via [L2capListener] factory function (no OS resources allocated)
 * - [open] allocates OS resources and starts accepting connections
 * - [close] stops accepting new connections; channels already accepted via
 *   [incoming] are NOT closed (caller owns their lifecycles)
 *
 * ## Channel ownership
 *
 * Each [L2capChannel] emitted from [incoming] is fully open and owned by the
 * consumer. The listener does not track them. Consumers must call
 * [L2capChannel.close] on each channel when done with it.
 */
public interface L2capListener : AutoCloseable {
    /**
     * PSM (Protocol/Service Multiplexer) assigned by the OS after [open].
     *
     * `0` before [open] completes successfully. Stable for the lifetime of
     * the listener once assigned.
     */
    public val psm: Int

    /**
     * Whether the listener is currently accepting connections.
     */
    public val isOpen: StateFlow<Boolean>

    /**
     * Flow of newly-accepted L2CAP channels.
     *
     * Buffered: up to 16 unconsumed connections are retained; older ones
     * are dropped if the buffer is full. Start collecting before calling
     * [open] to avoid missing early connections.
     */
    public val incoming: Flow<L2capChannel>

    /**
     * Open the listener and request a PSM from the OS.
     *
     * @param secure If true, requires encrypted connections.
     *   - Android: uses `BluetoothAdapter.listenUsingL2capChannel()` (encrypted)
     *     when true, `listenUsingInsecureL2capChannel()` when false.
     *   - iOS: passes the flag to `CBPeripheralManager.publishL2CAPChannel(withEncryption:)`.
     * @throws L2capException.PublishFailed if the OS rejects the publish request
     * @throws L2capException.InvalidState if already open
     * @throws L2capException.NotSupported if L2CAP server is unavailable
     */
    public suspend fun open(secure: Boolean = true)

    /**
     * Stop accepting connections and release the PSM.
     *
     * Previously accepted channels (emitted through [incoming]) remain open.
     * Safe to call multiple times.
     */
    override fun close()
}

/**
 * Create a platform-specific [L2capListener].
 *
 * - Android: backed by `BluetoothAdapter.listenUsingL2capChannel()`.
 *   Requires `BLUETOOTH_CONNECT` permission at runtime.
 * - iOS: backed by `CBPeripheralManager.publishL2CAPChannel()`.
 *   Shares the underlying `CBPeripheralManager` with `GattServer` and `Advertiser`.
 * - JVM: throws [L2capException.NotSupported] (no Bluetooth stack on JVM host).
 */
public expect fun L2capListener(): L2capListener
