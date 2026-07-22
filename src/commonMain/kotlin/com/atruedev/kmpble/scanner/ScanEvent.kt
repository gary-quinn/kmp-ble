package com.atruedev.kmpble.scanner

/**
 * Result of a BLE scan operation.
 *
 * Each emission is either a discovered [Advertisement] or a platform failure.
 * Consumers must handle both cases via exhaustive `when`:
 *
 * ```
 * scanner.scanEvents.collect { event ->
 *     when (event) {
 *         is ScanEvent.Found -> handleAdvertisement(event.advertisement)
 *         is ScanEvent.Failed -> handleError(event.error)
 *     }
 * }
 * ```
 */
public sealed interface ScanEvent {
    public data class Found(
        val advertisement: Advertisement,
    ) : ScanEvent

    public data class Failed(
        val error: ScanFailedException,
    ) : ScanEvent
}

public class ScanFailedException(
    public val errorCode: Int,
    message: String? = null,
) : Exception(message ?: "BLE scan failed with error code: $errorCode")
