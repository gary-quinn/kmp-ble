package com.atruedev.kmpble.isochronous

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Server-side LE Audio isochronous channel acceptor.
 *
 * Counterpart to [com.atruedev.kmpble.peripheral.Peripheral.openIsochronousChannel]
 * (client side). [open] publishes a BIG (Broadcast Isochronous Group) or
 * accepts CIG (Connected Isochronous Group) streams. Each accepted channel
 * is emitted through [incoming].
 *
 * ## Usage (BIG / broadcast source)
 *
 * ```kotlin
 * val listener = IsochronousListener()
 * listener.open()
 *
 * listener.incoming.collect { channel ->
 *     scope.launch {
 *         channel.incoming.collect { bytes -> handle(bytes) }
 *     }
 * }
 * ```
 *
 * ## Platform support
 *
 * - **Android:** Not publicly available. Throws [IsochronousException.NotSupported].
 * - **iOS:** CoreBluetooth does not expose isochronous listeners.
 *   Throws [IsochronousException.NotSupported].
 * - **JVM:** No Bluetooth stack. Throws [IsochronousException.NotSupported].
 *
 * ## Lifecycle
 *
 * - Created via [IsochronousListener] factory function (no OS resources allocated)
 * - [open] allocates OS resources and starts accepting streams
 * - [close] stops accepting new streams; previously accepted channels
 *   are NOT closed (caller owns their lifecycles)
 *
 * ## Channel ownership
 *
 * Each [IsochronousChannel] emitted from [incoming] is fully open and owned
 * by the consumer. The listener does not track them. Consumers must call
 * [IsochronousChannel.close] on each channel when done with it.
 */
public interface IsochronousListener : AutoCloseable {
    /**
     * Whether the listener is currently accepting connections.
     */
    public val isOpen: StateFlow<Boolean>

    /**
     * Hot stream of newly-accepted isochronous channels.
     *
     * Buffered: up to 16 unconsumed connections are retained. Start
     * collecting before calling [open] to avoid missing early connections.
     */
    public val incoming: SharedFlow<IsochronousChannel>

    /**
     * Open the listener and start accepting isochronous streams.
     *
     * @param secure If true, requires encrypted connections.
     * @param mtu Optional MTU hint propagated to each accepted channel.
     * @throws IsochronousException.NotSupported if isochronous channels
     *   are not available on this platform
     */
    public suspend fun open(
        secure: Boolean = true,
        mtu: Int? = null,
    )

    /**
     * Stop accepting connections.
     *
     * Previously accepted channels (emitted through [incoming]) remain open.
     * Safe to call multiple times.
     */
    override fun close()
}

/**
 * Create a platform-specific [IsochronousListener].
 *
 * - Android: throws [IsochronousException.NotSupported] (no public API)
 * - iOS: throws [IsochronousException.NotSupported] (CoreBluetooth limitation)
 * - JVM: throws [IsochronousException.NotSupported] (no Bluetooth stack)
 */
public expect fun IsochronousListener(): IsochronousListener
