package com.atruedev.kmpble.scanner

/**
 * Scan strategy controlling how aggressively the platform searches for devices.
 *
 * | ------- | -------------------------------------------------- |
 * | Android | `ScanSettings.Builder.setScanMode(int)`            |
 * | iOS     | No public equivalent; CoreBluetooth chooses        |
 * |         | dynamically based on RSSI and connection intent.   |
 */
public enum class ScanMode {
    /** Low power. OS decides scan intervals. Best battery life. */
    LowPower,

    /**
     * Balanced default. Moderate latency with reasonable power use.
     * Preferred for general-purpose scanning.
     */
    Balanced,

    /** Maximum throughput. Minimal latency at highest power cost. */
    LowLatency,
}
