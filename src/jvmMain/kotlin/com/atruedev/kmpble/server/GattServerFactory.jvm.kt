@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.server

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.newAdvertiser
import com.atruedev.kmpble.backend.newExtendedAdvertiser
import com.atruedev.kmpble.backend.newGattServer

public actual fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer =
    BleBackends.require("GattServer").newGattServer(builder)

public actual fun Advertiser(): Advertiser = BleBackends.require("Advertiser").newAdvertiser()

@ExperimentalBleApi
public actual fun ExtendedAdvertiser(): ExtendedAdvertiser =
    BleBackends.require("ExtendedAdvertiser").newExtendedAdvertiser()
