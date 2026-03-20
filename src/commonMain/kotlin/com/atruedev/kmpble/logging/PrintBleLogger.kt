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
        val message = when (event) {
            is BleLogEvent.ScanStarted -> "[Scan] Started (${event.filterCount} filters)"
            is BleLogEvent.ScanStopped -> "[Scan] Stopped: ${event.reason}"
            is BleLogEvent.AdvertisementReceived -> "[Scan] ${event.name ?: "Unknown"} (${event.identifier.value}) rssi=${event.rssi}"
            is BleLogEvent.StateTransition -> {
                val dur = event.durationInPreviousState.inWholeMilliseconds
                "[${event.identifier.value}] ${event.from::class.simpleName} → ${event.to::class.simpleName} (${dur}ms in previous)"
            }
            is BleLogEvent.GattOperation -> "[${event.identifier.value}] ${event.operation} uuid=${event.uuid} status=${event.status}"
            is BleLogEvent.DataTransfer -> "[${event.identifier.value}] ${event.direction} uuid=${event.uuid} ${event.bytes} bytes"
            is BleLogEvent.BondEvent -> "[${event.identifier.value}] Bond: ${event.event}"
            is BleLogEvent.Error -> "[${event.identifier?.value ?: "global"}] ERROR: ${event.message}"
            is BleLogEvent.StateRestoration -> "[StateRestoration] ${event.identifier?.value ?: "global"}: ${event.event}"
            is BleLogEvent.ServerLifecycle -> "[Server] ${event.event}"
            is BleLogEvent.ServerClientEvent -> "[Server] ${event.device.value}: ${event.event}"
            is BleLogEvent.ServerRequest -> "[Server] ${event.device.value} ${event.operation} uuid=${event.uuid} status=${event.status}"
        }
        println("kmp-ble: $message")
    }
}
