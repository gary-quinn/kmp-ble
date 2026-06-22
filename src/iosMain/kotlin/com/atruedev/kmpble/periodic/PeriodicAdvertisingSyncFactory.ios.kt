package com.atruedev.kmpble.periodic

import com.atruedev.kmpble.Identifier
import kotlin.time.Duration

public actual fun PeriodicAdvertisingSync(
    advertiserAddress: Identifier,
    advertisingSid: Int,
    timeout: Duration,
): PeriodicAdvertisingSync =
    throw PastException.NotSupported(
        "CoreBluetooth does not expose Periodic Advertising Sync",
    )
