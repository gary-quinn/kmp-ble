package com.atruedev.kmpble.logging

import platform.Foundation.NSLog

/**
 * [BleLogger] that emits structured BLE events to the Apple unified logging system
 * via NSLog. In production apps, replace with `os_log` for category-based filtering.
 *
 * ```kotlin
 * BleLogConfig.logger = IosLogBleLogger()
 * ```
 */
public class IosLogBleLogger : BleLogger {
    override fun log(event: BleLogEvent) {
        NSLog("kmp-ble: %@", event.formatted)
    }

    public companion object {
        /** Subsystem identifier matching the library bundle for os_log filtering. */
        public const val SUBSYSTEM: String = "com.atruedev.kmpble"
    }
}
