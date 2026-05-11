package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeripheralCodecTest {

    @Test
    fun readAsDecodesValue() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf(0x55) }
                }
            }
        }
        peripheral.connect()
        val level = peripheral.readAs(uuid("180f"), uuid("2a19"), Uint8Codec)
        assertEquals(0x55, level)
    }

    @Test
    fun readAsReturnsNullWhenCharacteristicMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        assertNull(peripheral.readAs(uuid("180f"), uuid("2a19"), Uint8Codec))
    }

    @Test
    fun readAsReturnsNullWhenDecoderFails() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf() }
                }
            }
        }
        peripheral.connect()
        assertNull(peripheral.readAs(uuid("180f"), uuid("2a19"), Uint8Codec))
    }

    @Test
    fun writeAsEncodesAndWrites() = runTest {
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
        peripheral.writeAs(uuid("180f"), uuid("2a19"), 0xAB, Uint8Codec)
        assertEquals(0xAB.toByte(), received?.get(0))
    }

    @Test
    fun writeAsNoOpsWhenCharacteristicMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        peripheral.writeAs(uuid("180f"), uuid("2a19"), 0xAB, Uint8Codec)
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
    fun observeAsSkipsDecodeFailures() = runTest {
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
        val values = peripheral.observeAs(uuid("180f"), uuid("2a19"), Uint8Codec).toList()
        assertEquals(listOf(0x10, 0x20), values)
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
