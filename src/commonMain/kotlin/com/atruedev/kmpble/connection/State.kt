package com.atruedev.kmpble.connection

import com.atruedev.kmpble.error.BleError

/**
 * Peripheral connection state machine - 14 states organized into four phases.
 *
 * Observe via [com.atruedev.kmpble.peripheral.Peripheral.state]. The state machine uses a
 * declarative transition table with no invalid transitions possible at compile time.
 */
public sealed interface State {
    /** Stable display name for logging. Derived from class name with K/N null fallback. */
    public val displayName: String get() = this::class.simpleName ?: "Unknown"

    /** The peripheral is establishing a connection. */
    public sealed interface Connecting : State {
        /** BLE transport link is being established. */
        public data object Transport : Connecting

        /** Pairing/bonding authentication is in progress. */
        public data object Authenticating : Connecting

        /** GATT service discovery is in progress. */
        public data object Discovering : Connecting

        /** Post-discovery configuration (MTU negotiation, CCCD setup). */
        public data object Configuring : Connecting
    }

    /** The peripheral is connected and operational. */
    public sealed interface Connected : State {
        /** Fully connected - GATT operations may be performed. */
        public data object Ready : Connected

        /** The bond state changed while connected (e.g. bond lost or re-paired). */
        public data object BondingChange : Connected

        /** The remote GATT database changed - services need re-discovery. */
        public data object ServiceChanged : Connected
    }

    /** The peripheral is in the process of disconnecting. */
    public sealed interface Disconnecting : State {
        /** Disconnect was initiated by the local device. */
        public data object Requested : Disconnecting

        /** Disconnect was triggered by an error condition. */
        public data object Error : Disconnecting
    }

    /** The peripheral is disconnected. */
    public sealed interface Disconnected : State {
        /** Disconnected by a local [com.atruedev.kmpble.peripheral.Peripheral.disconnect] call. */
        public data object ByRequest : Disconnected

        /** The remote device terminated the connection. */
        public data object ByRemote : Disconnected

        /** Disconnected due to a [BleError]. */
        public data class ByError(
            val error: BleError,
        ) : Disconnected

        /** The connection attempt timed out. */
        public data object ByTimeout : Disconnected

        /** Disconnected by a system event (e.g. Bluetooth turned off, app backgrounded on iOS). */
        public data object BySystemEvent : Disconnected
    }
}
