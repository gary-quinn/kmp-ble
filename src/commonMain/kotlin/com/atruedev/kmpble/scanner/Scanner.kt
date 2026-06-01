package com.atruedev.kmpble.scanner

import kotlinx.coroutines.flow.Flow

/**
 * BLE scanner that discovers nearby advertising devices.
 *
 * [scanEvents] is a **cold Flow**. Creating a Scanner starts nothing - no OS
 * resources allocated. Scanning starts on first `collect()`, stops when the collector's
 * coroutine is cancelled. Multiple concurrent collectors share one underlying OS scan.
 *
 * Each emission is a [ScanEvent] - either [ScanEvent.Found] with a discovered
 * [Advertisement] or [ScanEvent.Failed] when the platform scan encounters an error.
 *
 * Call [close] when the scanner is no longer needed to release internal resources.
 *
 * Platform construction:
 * - Android: `AndroidScanner(context) { filters { ... } }`
 * - iOS: `IosScanner { filters { ... } }`
 */
public interface Scanner : AutoCloseable {
    public val scanEvents: Flow<ScanEvent>
}
