package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi

/**
 * Common contract of [Peripheral.supportsReliableWrite] / [Peripheral.writeReliable].
 *
 * The fake honestly reports NO reliable-write support (it delegates to the plain
 * write handler and cannot be atomic), so these tests pin the honest contract:
 * fake behaves like iOS -- supportsReliableWrite = false, writeReliable throws.
 *
 * The Android native reliable-write flow (AndroidPeripheralReliableWrite +
 * AndroidGattBridge + onReliableWriteCompleted dispatch) requires an
 * instrumented/device test against real BluetoothGatt reliable-write behavior;
 * tracked separately (see PR #625).
 */
@OptIn(ExperimentalUuidApi::class)
class WriteReliableTest {
    private fun createPeripheral(): FakePeripheral =
        FakePeripheral {
            service("180d") {
                characteristic("2a37") { properties(read = true, write = true) }
            }
        }

    @Test
    fun fakeReportsNoReliableWriteSupport() {
        val peripheral = createPeripheral()
        assertEquals(false, peripheral.supportsReliableWrite)
    }

    @Test
    fun writeReliableThrowsOnUnsupportedFake() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()
            val char =
                peripheral
                    .services
                    .value
                    .orEmpty()
                    .first()
                    .characteristics
                    .first()

            assertFailsWith<UnsupportedOperationException> {
                peripheral.writeReliable(char, byteArrayOf(0x01))
            }
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun regularWriteStillWorksOnFake() =
        runTest {
            var received: ByteArray? = null
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true, write = true)
                            onWrite { data, _ -> received = data }
                        }
                    }
                }
            peripheral.connect()
            val char =
                peripheral
                    .services
                    .value
                    .orEmpty()
                    .first()
                    .characteristics
                    .first()

            val data = byteArrayOf(0x01, 0x02, 0x03)
            peripheral.write(char, data, com.atruedev.kmpble.gatt.WriteType.WithResponse)

            assertEquals(data.toList(), received?.toList())
            peripheral.disconnect()
            peripheral.close()
        }
}
