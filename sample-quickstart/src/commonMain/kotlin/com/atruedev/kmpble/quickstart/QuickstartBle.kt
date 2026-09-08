package com.atruedev.kmpble.quickstart

import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.flow.flowOf

/**
 * Scan/connect backend for the quickstart UI.
 *
 * [RealQuickstartBle] uses platform [Scanner] and [toPeripheral]. [FakeQuickstartBle] uses
 * [FakeScanner] / [FakePeripheral] so automation never calls [toPeripheral] on fake ads.
 */
interface QuickstartBle {
    fun createScanner(): Scanner

    suspend fun connect(advertisement: Advertisement): Peripheral
}

object RealQuickstartBle : QuickstartBle {
    override fun createScanner(): Scanner =
        Scanner {
            emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
        }

    override suspend fun connect(advertisement: Advertisement): Peripheral = advertisement.toPeripheral()
}

object FakeQuickstartBle : QuickstartBle {
    const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
    const val DEVICE_NAME = "Fake Heart Sensor"

    override fun createScanner(): Scanner =
        FakeScanner {
            advertisement {
                identifier(DEVICE_ID)
                name(DEVICE_NAME)
                rssi(-55)
                serviceUuids("180d")
            }
        }

    override suspend fun connect(advertisement: Advertisement): Peripheral =
        FakePeripheral {
            identifier = advertisement.identifier
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true, read = true)
                    onRead { byteArrayOf(0x00, 72) }
                    onObserve { flowOf(byteArrayOf(0x00, 72), byteArrayOf(0x00, 85)) }
                }
            }
        }
}

fun defaultQuickstartBle(): QuickstartBle =
    if (QuickstartConfig.useFakeBle) {
        FakeQuickstartBle
    } else {
        RealQuickstartBle
    }
