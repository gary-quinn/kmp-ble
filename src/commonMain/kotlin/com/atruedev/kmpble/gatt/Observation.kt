package com.atruedev.kmpble.gatt

/**
 * An event emitted by [com.atruedev.kmpble.peripheral.Peripheral.observe] for a characteristic
 * notification or indication subscription.
 *
 * Observations survive disconnection — a [Disconnected] event is emitted on disconnect, and
 * [Value] events resume automatically when the peripheral reconnects and resubscribes.
 */
public sealed interface Observation {
    /** A notification or indication payload received from the peripheral. */
    public data class Value(val data: ByteArray) : Observation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Value) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /** The peripheral disconnected. The flow remains active and will emit [Value] on reconnect. */
    public data object Disconnected : Observation
}
