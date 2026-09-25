package com.atruedev.kmpble.bluez

/**
 * BlueZ updates `GattCharacteristic1.Value` and emits `PropertiesChanged` after every
 * `ReadValue`, so a read would otherwise reach observers as a notification. Signals for a
 * characteristic with a read in flight are held until the read returns; the first one equal to
 * the read result (before or shortly after the reply) is dropped, the rest are released in order.
 */
internal class BlueZReadEchoFilter(
    private val echoWindowNanos: Long = ECHO_WINDOW_NANOS,
    private val clock: () -> Long = System::nanoTime,
) {
    private class Echo(
        val value: ByteArray,
        val at: Long,
    )

    private val lock = Any()
    private val reading = mutableMapOf<String, MutableList<ByteArray>>()
    private val echoes = mutableMapOf<String, Echo>()

    fun beginRead(path: String) {
        synchronized(lock) { reading[path] = mutableListOf() }
    }

    /** Ends a read of [path]; returns the held signal values to deliver as notifications. */
    fun endRead(
        path: String,
        value: ByteArray?,
    ): List<ByteArray> =
        synchronized(lock) {
            val held = reading.remove(path).orEmpty().toMutableList()
            if (value != null) {
                val echoIndex = held.indexOfFirst { it.contentEquals(value) }
                if (echoIndex >= 0) held.removeAt(echoIndex) else echoes[path] = Echo(value, clock())
            }
            held
        }

    /** True when a `Value` signal for [path] should be delivered now. */
    fun admit(
        path: String,
        value: ByteArray,
    ): Boolean =
        synchronized(lock) {
            reading[path]?.let {
                it += value
                return false
            }
            val echo = echoes.remove(path) ?: return true
            !(echo.value.contentEquals(value) && clock() - echo.at <= echoWindowNanos)
        }

    private companion object {
        const val ECHO_WINDOW_NANOS = 500_000_000L
    }
}
