package com.atruedev.kmpble.server

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.unsupportedBle

public actual fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer = unsupportedBle("GattServer")

public actual fun Advertiser(): Advertiser = unsupportedBle("Advertiser")

@ExperimentalBleApi
public actual fun ExtendedAdvertiser(): ExtendedAdvertiser = unsupportedBle("ExtendedAdvertiser")
