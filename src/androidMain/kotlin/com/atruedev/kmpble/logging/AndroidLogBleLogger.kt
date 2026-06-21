package com.atruedev.kmpble.logging

import android.util.Log

/**
 * [BleLogger] that emits structured BLE events to Android logcat.
 *
 * Events are tagged with `kmp-ble` so `adb logcat -s kmp-ble` isolates
 * BLE traffic from other application logs.
 *
 * ```kotlin
 * BleLogConfig.logger = AndroidLogBleLogger()
 * ```
 */
public class AndroidLogBleLogger : BleLogger {
    override fun log(event: BleLogEvent) {
        Log.d(TAG, event.formatted)
    }

    public companion object {
        private const val TAG = "kmp-ble"
    }
}
