package com.atruedev.kmpble.logging

/**
 * Simple logger that prints to stdout. Use for development/debugging.
 *
 * ```kotlin
 * BleLogConfig.logger = PrintBleLogger()
 * ```
 */
public class PrintBleLogger : BleLogger {
    override fun log(event: BleLogEvent) {
        println("kmp-ble: ${event.formatted}")
    }
}
