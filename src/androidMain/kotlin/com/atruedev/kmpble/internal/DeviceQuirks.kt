package com.atruedev.kmpble.internal

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Internal registry of device-specific BLE workarounds.
 *
 * Android BLE behavior varies significantly across OEMs and device models.
 * This registry provides automatic workarounds for known issues.
 *
 * ## Adding a new quirk
 *
 * 1. Identify the device: `adb shell getprop | grep -E "ro.product.(manufacturer|model|display)"`
 * 2. Add entry to the appropriate quirk map below
 * 3. Key format: `"manufacturer"` or `"manufacturer:model"` or `"manufacturer:model:display"`
 * 4. Submit PR with device info and description of the issue
 *
 * ## Matching priority
 *
 * 1. Exact match: `manufacturer:model:display`
 * 2. Model match: `manufacturer:model`
 * 3. Model prefix match: `manufacturer:model-prefix` (first 6 chars, for series matching)
 * 4. Manufacturer match: `manufacturer`
 * 5. Default value
 */
internal class DeviceQuirks(private val currentDevice: DeviceInfo) {

    companion object {
        private val instance: DeviceQuirks by lazy { DeviceQuirks(DeviceInfo.current()) }

        fun shouldBondBeforeConnect(): Boolean = instance.shouldBondBeforeConnect()
        fun gattConnectionRetryDelay(): Duration = instance.gattConnectionRetryDelay()
        fun connectGattRetryCount(): Int = instance.connectGattRetryCount()
        fun shouldRefreshServicesOnBond(): Boolean = instance.shouldRefreshServicesOnBond()
        fun bondStateChangeTimeout(): Duration = instance.bondStateChangeTimeout()
        fun connectionTimeout(): Duration = instance.connectionTimeout()
    }

    // =========================================================================
    // QUIRK: Bond before connect
    // Some Samsung devices fail to connect unless already bonded.
    // =========================================================================

    private val bondBeforeConnect: Set<String> = setOf(
        "samsung:sm-g99",      // Galaxy S21 series
        "samsung:sm-g98",      // Galaxy S20 series
        "samsung:sm-g97",      // Galaxy S10 series
        "samsung:sm-n98",      // Galaxy Note 20 series
        "samsung:sm-n97",      // Galaxy Note 10 series
        "samsung:sm-a52",      // Galaxy A52
        "samsung:sm-a53",      // Galaxy A53
    )

    fun shouldBondBeforeConnect(): Boolean {
        return matchesAny(bondBeforeConnect)
    }

    // =========================================================================
    // QUIRK: GATT connection retry delay
    // Pixel devices often return GATT 133 on first attempt. Retry after delay.
    // =========================================================================

    private val gattRetryDelay: Map<String, Duration> = mapOf(
        "google" to 1.seconds,
        "google:pixel" to 1.seconds,
        "google:pixel 6" to 1500.milliseconds,
        "google:pixel 7" to 1500.milliseconds,
        "google:pixel 8" to 1500.milliseconds,
        "samsung" to 500.milliseconds,
    )

    private val defaultGattRetryDelay: Duration = 300.milliseconds

    fun gattConnectionRetryDelay(): Duration {
        return matchFirst(gattRetryDelay) ?: defaultGattRetryDelay
    }

    // =========================================================================
    // QUIRK: GATT connection retry count
    // Number of times to retry connectGatt() on failure.
    // =========================================================================

    private val gattRetryCount: Map<String, Int> = mapOf(
        "google" to 3,
        "google:pixel" to 3,
        "samsung" to 2,
        "xiaomi" to 2,
        "oneplus" to 2,
    )

    private val defaultGattRetryCount: Int = 1

    fun connectGattRetryCount(): Int {
        return matchFirst(gattRetryCount) ?: defaultGattRetryCount
    }

    // =========================================================================
    // QUIRK: Refresh services after bond
    // OnePlus/Xiaomi cache services incorrectly after bonding.
    // =========================================================================

    private val refreshServicesOnBond: Set<String> = setOf(
        "oneplus",
        "xiaomi",
        "redmi",
        "poco",
        "oppo",
    )

    fun shouldRefreshServicesOnBond(): Boolean {
        return matchesAny(refreshServicesOnBond)
    }

    // =========================================================================
    // QUIRK: Bond state change timeout
    // Xiaomi devices delay bond callbacks significantly.
    // =========================================================================

    private val bondStateTimeout: Map<String, Duration> = mapOf(
        "xiaomi" to 15.seconds,
        "redmi" to 15.seconds,
        "poco" to 15.seconds,
        "huawei" to 10.seconds,
        "honor" to 10.seconds,
    )

    private val defaultBondStateTimeout: Duration = 10.seconds

    fun bondStateChangeTimeout(): Duration {
        return matchFirst(bondStateTimeout) ?: defaultBondStateTimeout
    }

    // =========================================================================
    // QUIRK: Connection timeout
    // Some devices need longer connection timeouts.
    // =========================================================================

    private val connectionTimeoutMap: Map<String, Duration> = mapOf(
        "samsung" to 30.seconds,
        "huawei" to 35.seconds,
        "honor" to 35.seconds,
    )

    private val defaultConnectionTimeout: Duration = 30.seconds

    fun connectionTimeout(): Duration {
        return matchFirst(connectionTimeoutMap) ?: defaultConnectionTimeout
    }

    // =========================================================================
    // Matching logic
    // =========================================================================

    /**
     * Check if current device matches any entry in the set.
     * Tries most specific match first, then progressively less specific.
     */
    private fun matchesAny(entries: Set<String>): Boolean {
        val keys = generateMatchKeys()
        return keys.any { it in entries }
    }

    /**
     * Find first matching value in the map.
     * Tries most specific match first, then progressively less specific.
     */
    private fun <T> matchFirst(entries: Map<String, T>): T? {
        val keys = generateMatchKeys()
        for (key in keys) {
            entries[key]?.let { return it }
        }
        return null
    }

    /**
     * Generate match keys from most specific to least specific:
     * 1. `manufacturer:model:display` (exact)
     * 2. `manufacturer:model` (any display/firmware)
     * 3. `manufacturer:model-prefix` (e.g., "samsung:sm-g99" matches "sm-g990", "sm-g991")
     * 4. `manufacturer` (any model)
     */
    private fun generateMatchKeys(): List<String> {
        val d = currentDevice
        return listOf(
            "${d.manufacturer}:${d.model}:${d.display}",
            "${d.manufacturer}:${d.model}",
            "${d.manufacturer}:${d.model.take(6)}",
            d.manufacturer,
        )
    }
}
