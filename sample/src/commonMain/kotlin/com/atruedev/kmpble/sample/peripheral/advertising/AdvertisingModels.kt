package com.atruedev.kmpble.sample.peripheral.advertising

import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower

sealed interface AdvertisingState {
    data object Stopped : AdvertisingState

    data object Advertising : AdvertisingState

    data class Error(
        val message: String,
    ) : AdvertisingState
}

data class AdvertisingUiState(
    val state: AdvertisingState = AdvertisingState.Stopped,
    val localName: String = "BLE Toolkit",
    val connectable: Boolean = true,
    val mode: AdvertiseMode = AdvertiseMode.Balanced,
    val txPower: AdvertiseTxPower = AdvertiseTxPower.Medium,
)
