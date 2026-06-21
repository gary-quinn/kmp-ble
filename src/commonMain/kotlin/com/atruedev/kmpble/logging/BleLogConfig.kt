package com.atruedev.kmpble.logging

import kotlinx.atomicfu.atomic

/**
 * Global logging configuration.
 *
 * ```kotlin
 * // Enable logging
 * BleLogConfig.logger = PrintBleLogger()
 *
 * // Plug in your own
 * BleLogConfig.logger = BleLogger { event ->
 *     Timber.d("BLE: $event")
 * }
 *
 * // Disable
 * BleLogConfig.logger = null
 * ```
 */
public object BleLogConfig {
    /**
     * Atomic read/write for thread-safe logger swaps at runtime.
     * Atomicfu guarantees visibility without JVM-specific annotations.
     */
    private val _logger = atomic<BleLogger?>(null)
    public var logger: BleLogger?
        get() = _logger.value
        set(value) {
            _logger.value = value
        }

    /** Throws on invalid state transitions when enabled. Set once at app startup. */
    private val _strictMode = atomic(false)
    public var strictMode: Boolean
        get() = _strictMode.value
        set(value) {
            _strictMode.value = value
        }
}

internal fun logEvent(event: BleLogEvent) {
    BleLogConfig.logger?.log(event)
}
