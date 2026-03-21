package com.atruedev.kmpble.server

import com.atruedev.kmpble.ExperimentalBleApi

public actual fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer {
    val config = GattServerBuilder().apply(builder)
    return IosGattServer(config.services)
}

public actual fun Advertiser(): Advertiser {
    return IosAdvertiser()
}

@ExperimentalBleApi
public actual fun ExtendedAdvertiser(): ExtendedAdvertiser {
    return IosExtendedAdvertiser()
}
