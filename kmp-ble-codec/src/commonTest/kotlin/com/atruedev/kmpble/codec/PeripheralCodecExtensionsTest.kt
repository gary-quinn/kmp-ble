package com.atruedev.kmpble.codec

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PeripheralCodecExtensionsTest {
    private val svc = uuidFrom("180f")
    private val char = uuidFrom("2a19")

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
        val result = peripheral.readAs(svc, char, Uint8Codec)
        assertEquals(0x55, result.getOrThrow())
    }

    @Test
    fun readAsFailsWithCharacteristicNotFoundWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val result = peripheral.readAs(svc, char, Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<CharacteristicNotFoundException>(ex)
        assertEquals(svc, ex.serviceUuid)
        assertEquals(char, ex.characteristicUuid)
    }

    @Test
    fun readAsFailsWithDecodeFailureWhenDecoderThrows() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf() }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.readAs(svc, char, Uint8Codec)
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
        val result = peripheral.writeAs(svc, char, 0xAB, Uint8Codec)
        assertTrue(result.isSuccess)
        assertContentEquals(byteArrayOf(0xAB.toByte()), received)
    }

    @Test
    fun writeAsFailsWithCharacteristicNotFoundWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val result = peripheral.writeAs(svc, char, 0xAB, Uint8Codec)
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
        val values = peripheral.observeAs(svc, char, Uint8Codec).toList()
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
                svc,
                char,
                Uint8Codec,
                backpressure = BackpressureStrategy.Unbounded,
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
        val values = peripheral.observeAs(svc, char, Uint8Codec).toList()
        assertTrue(values.isEmpty())
    }
}
