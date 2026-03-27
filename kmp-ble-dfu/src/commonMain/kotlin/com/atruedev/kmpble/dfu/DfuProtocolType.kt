package com.atruedev.kmpble.dfu

/**
 * Supported DFU protocol families.
 *
 * @see DfuDetector.detect
 */
public enum class DfuProtocolType {
    /** Nordic Semiconductor Secure DFU v2. */
    NORDIC,

    /** MCUboot SMP-based DFU (Zephyr, Mynewt). */
    MCUBOOT,

    /** Espressif ESP-IDF OTA. */
    ESP_OTA,
}
