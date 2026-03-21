package com.atruedev.kmpble.quirks.oem

import com.atruedev.kmpble.quirks.BleQuirks
import com.atruedev.kmpble.quirks.DeviceMatch
import com.atruedev.kmpble.quirks.QuirkProvider
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Curated OEM-specific BLE workarounds.
 *
 * Discovered at runtime via [java.util.ServiceLoader]. Adding a new quirk:
 * 1. Identify the device: `adb shell getprop | grep -E "ro.product.(manufacturer|model|display)"`
 * 2. Add entry to the appropriate table below
 * 3. Key format: `"manufacturer"` or `"manufacturer:model"` or `"manufacturer:model:display"`
 */
public class OemQuirkProvider : QuirkProvider {

    override fun contribute(builder: QuirkRegistry.Builder) {
        builder.register(BleQuirks.BondBeforeConnect, true) {
            DeviceMatch.matchesAny(it, BOND_BEFORE_CONNECT)
        }
        builder.register(BleQuirks.GattRetryDelay, GATT_RETRY_DELAY)
        builder.register(BleQuirks.GattRetryCount, GATT_RETRY_COUNT)
        builder.register(BleQuirks.RefreshServicesOnBond, true) {
            DeviceMatch.matchesAny(it, REFRESH_SERVICES_ON_BOND)
        }
        builder.register(BleQuirks.BondStateTimeout, BOND_STATE_TIMEOUT)
        builder.register(BleQuirks.ConnectionTimeout, CONNECTION_TIMEOUT)
    }

    private companion object {

        // Some Samsung devices fail to connect unless already bonded.
        val BOND_BEFORE_CONNECT: Set<String> = setOf(
            "samsung:sm-g99",      // Galaxy S21 series
            "samsung:sm-g98",      // Galaxy S20 series
            "samsung:sm-g97",      // Galaxy S10 series
            "samsung:sm-n98",      // Galaxy Note 20 series
            "samsung:sm-n97",      // Galaxy Note 10 series
            "samsung:sm-a52",      // Galaxy A52
            "samsung:sm-a53",      // Galaxy A53
        )

        // Pixel devices often return GATT 133 on first attempt — retry after delay.
        val GATT_RETRY_DELAY: Map<String, Duration> = mapOf(
            "google" to 1.seconds,
            "google:pixel" to 1.seconds,
            "google:pixel 6" to 1500.milliseconds,
            "google:pixel 7" to 1500.milliseconds,
            "google:pixel 8" to 1500.milliseconds,
            "samsung" to 500.milliseconds,
        )

        val GATT_RETRY_COUNT: Map<String, Int> = mapOf(
            "google" to 3,
            "google:pixel" to 3,
            "samsung" to 2,
            "xiaomi" to 2,
            "oneplus" to 2,
        )

        // OnePlus/Xiaomi cache services incorrectly after bonding.
        val REFRESH_SERVICES_ON_BOND: Set<String> = setOf(
            "oneplus",
            "xiaomi",
            "redmi",
            "poco",
            "oppo",
        )

        // Xiaomi devices delay bond callbacks significantly.
        val BOND_STATE_TIMEOUT: Map<String, Duration> = mapOf(
            "xiaomi" to 15.seconds,
            "redmi" to 15.seconds,
            "poco" to 15.seconds,
            "huawei" to 10.seconds,
            "honor" to 10.seconds,
        )

        val CONNECTION_TIMEOUT: Map<String, Duration> = mapOf(
            "samsung" to 30.seconds,
            "huawei" to 35.seconds,
            "honor" to 35.seconds,
        )
    }
}
