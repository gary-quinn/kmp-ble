package com.atruedev.kmpble.connection

import com.atruedev.kmpble.error.BleError

public sealed interface State {
    /** Stable display name for logging. Derived from class name with K/N null fallback. */
    public val displayName: String get() = this::class.simpleName ?: "Unknown"

    public sealed interface Connecting : State {
        public data object Transport : Connecting

        public data object Authenticating : Connecting

        public data object Discovering : Connecting

        public data object Configuring : Connecting
    }

    public sealed interface Connected : State {
        public data object Ready : Connected

        public data object BondingChange : Connected

        public data object ServiceChanged : Connected
    }

    public sealed interface Disconnecting : State {
        public data object Requested : Disconnecting

        public data object Error : Disconnecting
    }

    public sealed interface Disconnected : State {
        public data object ByRequest : Disconnected

        public data object ByRemote : Disconnected

        public data class ByError(val error: BleError) : Disconnected

        public data object ByTimeout : Disconnected

        public data object BySystemEvent : Disconnected
    }
}
