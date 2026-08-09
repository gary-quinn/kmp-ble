package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.connectAndDiscover
import com.atruedev.kmpble.peripheral.toPeripheral
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scan for the first [Advertisement] matching [predicate], connect to it, and
 * return the connected [Peripheral] with services discovered.
 *
 * Combines the common "scan -> match -> connect -> discover" workflow into a
 * single call, reusing [com.atruedev.kmpble.peripheral.connectAndDiscover]
 * internally. The scanner is cold: scanning starts on collect, stops on match.
 *
 * ```
 * val peripheral = scanner.scanAndConnect { it.name == "HeartSensor" }
 * try {
 *     val hr = peripheral.findCharacteristic(uuidFrom("180D"), uuidFrom("2A37"))
 *     peripheral.read(hr!!)
 * } finally {
 *     peripheral.close()
 * }
 * ```
 *
 * @param scanTimeout How long to scan for a matching device before giving up.
 * @param predicate Match rule applied to each scan result.
 * @param connectOptions Options passed to [com.atruedev.kmpble.peripheral.connectAndDiscover].
 * @return A connected [Peripheral] with services discovered. The caller owns the
 *   returned peripheral and MUST call [Peripheral.close] when done.
 * @throws [ScanTimeoutException] if no device matches within [scanTimeout].
 * @throws com.atruedev.kmpble.error.BleException if connect or discovery fails.
 */
public suspend fun Scanner.scanAndConnect(
    scanTimeout: Duration = 30.seconds,
    connectOptions: ConnectionOptions = ConnectionOptions(),
    predicate: (Advertisement) -> Boolean = { true },
): Peripheral {
    val advertisement =
        withTimeoutOrNull(scanTimeout) {
            scanEvents
                .mapNotNull { event -> (event as? ScanEvent.Found)?.advertisement }
                .firstOrNull(predicate)
        } ?: throw ScanTimeoutException(scanTimeout)
    val peripheral = advertisement.toPeripheral()
    try {
        peripheral.connectAndDiscover(connectOptions)
    } catch (e: Exception) {
        peripheral.close()
        throw e
    }
    return peripheral
}

/**
 * Thrown when [Scanner.scanAndConnect] finds no matching peripheral within
 * the configured [scanTimeout].
 */
public class ScanTimeoutException(
    public val scanTimeout: Duration,
) : IllegalStateException("No matching peripheral found within $scanTimeout")
