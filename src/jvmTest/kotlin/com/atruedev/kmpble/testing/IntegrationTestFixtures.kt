package com.atruedev.kmpble.testing

import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakeScannerBuilder
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object IntegrationTestFixtures {
    val scanner: FakeScanner =
        FakeScannerBuilder()
            .apply {
                advertisement {
                    identifier("AA:BB:CC:DD:EE:FF")
                    name("IntegrationTestDevice")
                    rssi(-55)
                    serviceUuids("180d")
                }
            }
            .build()
}

