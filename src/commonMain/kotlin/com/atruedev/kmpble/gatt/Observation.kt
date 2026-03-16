package com.atruedev.kmpble.gatt

public sealed interface Observation {
    public data class Value(val data: ByteArray) : Observation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Value) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    public data object Disconnected : Observation
}
