package com.atruedev.kmpble.profiles.battery

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryProfileTest {

    @Test
    fun readBatteryLevelReturnsParsedValue() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf(82) }
                }
            }
        }
        peripheral.connect()
        assertEquals(82, peripheral.readBatteryLevel())
    }

    @Test
    fun readBatteryLevelReturnsNullWhenServiceMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        assertNull(peripheral.readBatteryLevel())
    }

    @Test
    fun batteryLevelNotificationsEmitsParsedValues() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve { flowOf(byteArrayOf(50), byteArrayOf(60)) }
                }
            }
        }
        peripheral.connect()
        assertEquals(50, peripheral.batteryLevelNotifications().first())
    }
}
