package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class WriteReliableTest {
    private fun createPeripheral(): FakePeripheral =
        FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(read = true, write = true)
                    onWrite { data, _ ->
                        assertTrue(data.isNotEmpty())
                    }
                }
            }
        }

    @Test
    fun writeReliableWritesSingleChunkData() =
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

            val data = byteArrayOf(0x01, 0x02, 0x03)
            peripheral.writeReliable(char, data)

            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun writeReliableAcceptsLargeData() =
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

            // Larger than the fake's default 20-byte max write length.
            val data = ByteArray(200) { it.toByte() }
            peripheral.writeReliable(char, data)

            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun writeReliableRecordsDataInHandler() =
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

            val data = byteArrayOf(0x0A, 0x0B, 0x0C)
            peripheral.writeReliable(char, data)

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
