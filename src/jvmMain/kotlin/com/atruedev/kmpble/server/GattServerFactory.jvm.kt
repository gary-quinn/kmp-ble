package com.atruedev.kmpble.server

public actual fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer =
    throw UnsupportedOperationException("BLE GATT server is not available on JVM")

public actual fun Advertiser(): Advertiser =
    throw UnsupportedOperationException("BLE advertising is not available on JVM")
