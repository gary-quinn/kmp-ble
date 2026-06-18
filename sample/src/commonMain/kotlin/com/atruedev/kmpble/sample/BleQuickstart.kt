package com.atruedev.kmpble.sample

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * End-to-end BLE quickstart demonstrating the full lifecycle:
 * scan -> connect -> discover -> read -> observe -> disconnect.
 *
 * This example uses structured concurrency, proper CancellationException handling,
 * and explicit resource lifecycle management -- the key patterns you need in production.
 *
 * Prerequisites:
 * - BLE permissions granted (handled by [PermissionGate] in the sample app)
 * - Bluetooth enabled on device
 * - A BLE peripheral advertising the Heart Rate service (0x180D) nearby
 */
suspend fun bleQuickstartHeartRate() =
    coroutineScope {
        // 1. Create scanner with emission policy to reduce redundant results.
        //    Scanner is cold -- scanning starts on first collect(), stops when cancelled.
        val scanner =
            Scanner {
                emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
            }

        val heartRateServiceUuid = uuidFrom("180D")
        val heartRateMeasurementUuid = uuidFrom("2A37")

        // 2. Scan for devices and pick the first one advertising Heart Rate.
        //    scanEvents is a cold Flow<ScanEvent>.
        val advertisement: Advertisement =
            try {
                scanner.scanEvents
                    .mapNotNull { event ->
                        when (event) {
                            is ScanEvent.Found -> {
                                if (heartRateServiceUuid in event.advertisement.serviceUuids) {
                                    event.advertisement
                                } else {
                                    null
                                }
                            }
                            is ScanEvent.Failed -> {
                                println("Scan error: ${event.error.message}")
                                null
                            }
                        }
                    }.first() // Suspends until first matching device found
            } finally {
                // Scanner resources released as soon as we have a device to connect to.
                scanner.close()
            }

        println("Found: ${advertisement.name ?: "Unknown"} (${advertisement.identifier})")

        // 3. Create Peripheral from the advertisement.
        //    toPeripheral() is the platform-specific factory that creates the right implementation.
        val peripheral = advertisement.toPeripheral()

        try {
            // 4. Connect with optional auto-reconnect.
            //    connect() suspends until connected or throws on failure.
            peripheral.connect(ConnectionOptions())
            println("Connected: ${peripheral.state.value}")

            // 5. Service discovery happens automatically after connection.
            //    Wait for services to be discovered before reading/writing.
            peripheral.services.first { it != null }
            val services = peripheral.services.value!!
            println("Discovered ${services.size} service(s)")

            // 6. Find the Heart Rate Measurement characteristic (0x2A37).
            val hrService = services.first { svc -> svc.uuid == heartRateServiceUuid }
            val hrChar =
                hrService.characteristics.first { char ->
                    char.uuid == heartRateMeasurementUuid
                }

            // 7. Observe notifications -- the Flow survives disconnects and auto-resubscribes.
            //    observeValues() provides transparent reconnection without emitting disconnect events.
            val hrJob =
                launch {
                    try {
                        peripheral
                            .observeValues(hrChar, BackpressureStrategy.Latest)
                            .collect { data ->
                                // Heart Rate Measurement format: flags byte + 1-2 bytes HR value
                                val flags = data[0].toInt() and 0xFF
                                val hrValue =
                                    if ((flags and 0x01) == 0) {
                                        // 8-bit heart rate (UINT8)
                                        data[1].toInt() and 0xFF
                                    } else {
                                        // 16-bit heart rate (UINT16, little-endian)
                                        ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                                    }
                                println("Heart Rate: $hrValue BPM")
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("Observation error: ${e.message}")
                    }
                }

            // 8. Read current value (poll once).
            val currentValue = peripheral.read(hrChar)
            println("Read ${currentValue.size} bytes")

            // 9. Let observations run for a while, then disconnect.
            //    In a real app, you'd keep the observation running for the app's lifetime.
            delay(30_000) // 30 seconds of heart rate monitoring

            // 10. Graceful shutdown: cancel observation, disconnect, close.
            hrJob.cancel()
            peripheral.disconnect()
        } finally {
            peripheral.close()
            println("Disconnected and cleaned up")
        }
    }

/**
 * Minimal end-to-end pattern: scan -> connect -> read -> disconnect.
 *
 * Use this as a starting point when you just need to read a value
 * from a specific BLE device without ongoing monitoring.
 */
suspend fun bleQuickstartReadOnce(
    targetName: String,
    serviceUuidHex: String,
    charUuidHex: String,
): ByteArray? =
    coroutineScope {
        val scanner =
            Scanner {
                emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
            }

        val advertisement: Advertisement =
            try {
                scanner.scanEvents
                    .mapNotNull { event ->
                        (event as? ScanEvent.Found)?.advertisement?.takeIf {
                            it.name == targetName
                        }
                    }.first()
            } finally {
                scanner.close()
            }

        val peripheral = advertisement.toPeripheral()
        try {
            peripheral.connect()
            peripheral.services.first { it != null }

            val targetServiceUuid = uuidFrom(serviceUuidHex)
            val targetCharUuid = uuidFrom(charUuidHex)

            val service =
                peripheral.services.value!!.first { svc ->
                    svc.uuid == targetServiceUuid
                }
            val characteristic =
                service.characteristics.first { char ->
                    char.uuid == targetCharUuid
                }

            val data = peripheral.read(characteristic)
            println("Read from $targetName ($serviceUuidHex/$charUuidHex): ${data.size} bytes")
            data
        } finally {
            peripheral.disconnect()
            peripheral.close()
        }
    }
