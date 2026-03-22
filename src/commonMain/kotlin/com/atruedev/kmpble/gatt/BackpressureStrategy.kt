package com.atruedev.kmpble.gatt

public sealed class BackpressureStrategy {
    public data object Latest : BackpressureStrategy()

    public data class Buffer(val capacity: Int) : BackpressureStrategy()

    public data object Unbounded : BackpressureStrategy()
}
