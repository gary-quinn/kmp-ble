package com.atruedev.kmpble.server

import com.atruedev.kmpble.KmpBle

public actual fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer {
    val config = GattServerBuilder().apply(builder)
    return AndroidGattServer(KmpBle.requireContext(), config.services)
}

public actual fun Advertiser(): Advertiser {
    return AndroidAdvertiser(KmpBle.requireContext())
}
