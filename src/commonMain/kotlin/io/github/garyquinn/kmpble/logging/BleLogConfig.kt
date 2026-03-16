package io.github.garyquinn.kmpble.logging

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
    @kotlin.concurrent.Volatile
    public var logger: BleLogger? = null
}

internal fun logEvent(event: BleLogEvent) {
    BleLogConfig.logger?.log(event)
}
