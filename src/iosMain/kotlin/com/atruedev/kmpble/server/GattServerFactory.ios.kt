package com.atruedev.kmpble.server

public actual fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer {
    val config = GattServerBuilder().apply(builder)
    return IosGattServer(config.services)
}

public actual fun Advertiser(): Advertiser {
    return IosAdvertiser()
}
