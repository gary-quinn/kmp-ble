package com.atruedev.kmpble.codec

/** Typed equivalent of [com.atruedev.kmpble.gatt.Observation]. */
public sealed interface DecodedObservation<out T> {
    public data class Value<T>(val value: T) : DecodedObservation<T>
    public data object Disconnected : DecodedObservation<Nothing>
}
