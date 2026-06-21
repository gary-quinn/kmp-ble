package com.atruedev.kmpble.quirks.ios

import com.atruedev.kmpble.quirks.BleQuirks
import com.atruedev.kmpble.quirks.DeviceMatch
import com.atruedev.kmpble.quirks.QuirkProvider
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Curated iOS-specific BLE workarounds.
 *
 * iOS BLE quirks differ from Android: Apple controls both hardware and the CoreBluetooth
 * stack, so quirks are rarer. Issues typically arise from:
 * - Specific iPhone/iPad models with known BLE chipset limitations
 * - iOS version-specific CoreBluetooth behavior changes
 * - Bluetooth 5.x feature availability by device generation
 *
 * Register with [com.atruedev.kmpble.quirks.IosQuirkProviders.register] before
 * calling [QuirkRegistry.getInstance].
 */
public class IosOemQuirkProvider : QuirkProvider {
    override fun contribute(builder: QuirkRegistry.Builder) {
        // iPhone SE (1st gen) and older devices have weaker BLE radios;
        // connection timeouts should be more generous.
        builder.register(BleQuirks.ConnectionTimeout, IOS_CONNECTION_TIMEOUT)
        // Some older models benefit from service refresh after bonding.
        builder.register(BleQuirks.RefreshServicesOnBond, true) {
            DeviceMatch.matchesAny(it, REFRESH_SERVICES_ON_BOND)
        }
    }

    private companion object {
        // iPhone SE 1st gen, iPhone 5s, iPhone 6 (A7/A8 chips) need more time.
        val IOS_CONNECTION_TIMEOUT: Map<String, Duration> =
            mapOf(
                "apple:iphone6,2" to 45.seconds,
                "apple:iphone6,1" to 45.seconds,
                "apple:iphone7,2" to 35.seconds,
                "apple:iphone7,1" to 35.seconds,
                "apple:iphone8,4" to 40.seconds, // iPhone SE 1st gen
            )

        // Older devices sometimes cache stale service data after bonding.
        val REFRESH_SERVICES_ON_BOND: Set<String> =
            setOf(
                "apple:iphone6",
                "apple:iphone7",
                "apple:iphone8,4", // iPhone SE 1st gen
            )
    }
}
