package com.atruedev.kmpble.quirks

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Well-known quirk keys used by the BLE connection logic. */
public object BleQuirks {
    /** Samsung: some Galaxy devices fail to connect unless already bonded. */
    public val BondBeforeConnect: QuirkKey<Boolean> =
        QuirkKey("bondBeforeConnect", false) { v, _ -> if (v) "bond-before-connect" else null }

    /** Pixel: GATT 133 on first attempt - retry after this delay. */
    public val GattRetryDelay: QuirkKey<Duration> =
        QuirkKey("gattRetryDelay", 300.milliseconds) { v, d -> if (v != d) "retry-delay=$v" else null }

    /** Number of times to retry `connectGatt()` on failure. */
    public val GattRetryCount: QuirkKey<Int> =
        QuirkKey("gattRetryCount", 1) { v, _ -> if (v > 1) "retry=${v}x" else null }

    /** OnePlus/Xiaomi: cache stale services after bonding - refresh required. */
    public val RefreshServicesOnBond: QuirkKey<Boolean> =
        QuirkKey("refreshServicesOnBond", false) { v, _ -> if (v) "refresh-services-on-bond" else null }

    /** Xiaomi: delays bond state callbacks significantly. */
    public val BondStateTimeout: QuirkKey<Duration> =
        QuirkKey("bondStateTimeout", 10.seconds) { v, d -> if (v != d) "bond-timeout=$v" else null }

    /** Some OEMs need longer connection timeouts. */
    public val ConnectionTimeout: QuirkKey<Duration> =
        QuirkKey("connectionTimeout", 30.seconds) { v, d -> if (v != d) "conn-timeout=$v" else null }
}
