package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Common behavior of [Peripheral.writeReliable].
 *
 * NOTE: these tests exercise the common API surface through [FakePeripheral],
 * which delegates writeReliable to its plain write handler. The Android native
 * reliable-write flow (AndroidPeripheralReliableWrite + AndroidGattBridge +
 * onReliableWriteCompleted dispatch) has no unit coverage -- it requires an
 * instrumented/device test against real BluetoothGatt reliable-write behavior,
 * tracked separately. See PR #625 verification note.
 */
@OptIn(ExperimentalUuidApi::class)
class WriteReliableTest {
    @Test
    fun fakePeripheralReportsSupportsReliableWrite() {
        val peripheral =
            FakePeripheral {
                service("180d") {
                    characteristic("2a37") { properties(read = true, write = true) }
                }
            }
        assertTrue(peripheral.supportsReliableWrite, "fake defaults to Android-like atomic support")
    }

    @Test
    fun writeReliableSingleChunkDeliversExactData() =
        runTest {
            var received: ByteArray? = null
            var writeCount = 0
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true, write = true)
                            onWrite { data, _ ->
                                received = data
                                writeCount++
                            }
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
            peripheral.writeReliable(char, data)

            assertEquals(1, writeCount, "single-chunk value must be written exactly once")
            assertEquals(data.toList(), received?.toList())
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun writeReliableLargeDataPassesThrough() =
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

            val data = ByteArray(200) { it.toByte() }
            peripheral.writeReliable(char, data)

            // The fake does not chunk (real platforms do via LargeWriteHandler), so
            // the handler sees the full payload in one call -- asserts pass-through.
            assertEquals(data.toList(), received?.toList())
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun writeReliableRejectsWhenCharacteristicNotWritable() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true) // no write property
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

            val result =
                runCatching {
                    peripheral.writeReliable(char, byteArrayOf(0x01))
                }
            assertTrue(result.isFailure, "write to non-writable characteristic must fail")
            peripheral.disconnect()
            peripheral.close()
        }
}
