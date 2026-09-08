package com.atruedev.kmpble.quickstart

import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.flow.flowOf

internal const val FAKE_DEVICE_ID = "AA:BB:CC:DD:EE:FF"
internal const val FAKE_DEVICE_NAME = "Fake Heart Sensor"

internal fun createQuickstartScanner(): Scanner {
    if (QuickstartConfig.useFakeBle) {
        return FakeScanner {
            advertisement {
                identifier(FAKE_DEVICE_ID)
                name(FAKE_DEVICE_NAME)
                rssi(-55)
                serviceUuids("180d")
            }
        }
    }
    return Scanner {
        emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
    }
}

internal suspend fun connectQuickstartDevice(advertisement: Advertisement): Peripheral {
    if (QuickstartConfig.useFakeBle) {
        return FakePeripheral {
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
    return advertisement.toPeripheral()
}
