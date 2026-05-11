package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PeripheralCodecTest {

    @Test
    fun readAsReturnsSuccessWithDecodedValue() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf(0x55) }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.readAs(uuid("180f"), uuid("2a19"), Uint8Codec)
        assertEquals(0x55, result.getOrThrow())
    }

    @Test
    fun readAsFailsWithCharacteristicNotFoundWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val result = peripheral.readAs(uuid("180f"), uuid("2a19"), Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<CharacteristicNotFoundException>(ex)
        assertEquals(uuid("180f"), ex.serviceUuid)
        assertEquals(uuid("2a19"), ex.characteristicUuid)
    }

    @Test
    fun readAsFailsWithDecodeFailureWhenDecoderReturnsNull() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf() }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.readAs(uuid("180f"), uuid("2a19"), Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<DecodeFailureException>(ex)
        assertEquals(0, ex.bytes.size)
    }

    @Test
    fun writeAsReturnsSuccessAndEncodesValue() = runTest {
        var received: ByteArray? = null
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(write = true)
                    onWrite { bytes, _ -> received = bytes }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.writeAs(uuid("180f"), uuid("2a19"), 0xAB, Uint8Codec)
        assertTrue(result.isSuccess)
        assertContentEquals(byteArrayOf(0xAB.toByte()), received)
    }

    @Test
    fun writeAsFailsWithCharacteristicNotFoundWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val result = peripheral.writeAs(uuid("180f"), uuid("2a19"), 0xAB, Uint8Codec)
        assertIs<CharacteristicNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun observeAsDecodesNotifications() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve { flowOf(byteArrayOf(0x10), byteArrayOf(0x20)) }
                }
            }
        }
        peripheral.connect()
        val values = peripheral.observeAs(uuid("180f"), uuid("2a19"), Uint8Codec).toList()
        assertEquals(listOf(0x10, 0x20), values)
    }

    @Test
    fun observeAsRoutesDecodeFailuresToCallback() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve {
                        flowOf(byteArrayOf(0x10), byteArrayOf(), byteArrayOf(0x20))
                    }
                }
            }
        }
        peripheral.connect()
        val failures = mutableListOf<ByteArray>()
        val values = peripheral
            .observeAs(
                uuid("180f"),
                uuid("2a19"),
                Uint8Codec,
                backpressure = com.atruedev.kmpble.gatt.BackpressureStrategy.Unbounded,
                onDecodeFailure = { failures.add(it) },
            )
            .toList()
        assertEquals(listOf(0x10, 0x20), values)
        assertEquals(1, failures.size)
        assertEquals(0, failures[0].size)
    }

    @Test
    fun observeAsReturnsEmptyWhenCharacteristicMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val values = peripheral.observeAs(uuid("180f"), uuid("2a19"), Uint8Codec).toList()
        assertTrue(values.isEmpty())
    }
}

private fun uuid(s: String) = com.atruedev.kmpble.scanner.uuidFrom(s)
