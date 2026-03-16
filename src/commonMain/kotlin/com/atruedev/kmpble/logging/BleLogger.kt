package com.atruedev.kmpble.logging

/**
 * Pluggable logging interface for BLE events.
 *
 * Set on [BleLogConfig] at library initialization. All peripherals and scanners
 * emit structured [BleLogEvent]s — consumers forward to Timber, OSLog, Kermit,
 * or any logging framework.
 *
 * Data payloads are logged by byte count only by default — no accidental PHI/PII in logs.
 */
public fun interface BleLogger {
    public fun log(event: BleLogEvent)
}
