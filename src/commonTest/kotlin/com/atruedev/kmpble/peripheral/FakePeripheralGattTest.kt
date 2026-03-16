package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralGattTest {

    @Test
    fun readCharacteristic() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(read = true)
                    onRead { byteArrayOf(0x00, 72) }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        val value = peripheral.read(char)
        assertContentEquals(byteArrayOf(0x00, 72), value)
    }

    @Test
    fun readWithoutHandlerThrows() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") { properties(read = true) }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        assertFailsWith<UnsupportedOperationException> {
            peripheral.read(char)
        }
    }

    @Test
    fun readWhileDisconnectedThrows() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(read = true)
                    onRead { byteArrayOf(0x00, 72) }
                }
            }
        }

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))
        // char is null before connect (services not populated)
        assertEquals(null, char)
    }

    @Test
    fun writeCharacteristic() = runTest {
        var writtenData: ByteArray? = null
        var writtenType: WriteType? = null

        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a39") {
                    properties(write = true)
                    onWrite { data, type ->
                        writtenData = data
                        writtenType = type
                    }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
        peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)

        assertContentEquals(byteArrayOf(0x01), writtenData)
        assertEquals(WriteType.WithResponse, writtenType)
    }

    @Test
    fun observeCharacteristic() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true)
                    onObserve {
                        flow {
                            emit(byteArrayOf(0x00, 72))
                            emit(byteArrayOf(0x00, 80))
                            emit(byteArrayOf(0x00, 65))
                        }
                    }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        val observations = peripheral.observe(char, BackpressureStrategy.Unbounded).toList()

        assertEquals(3, observations.size)
        assertIs<Observation.Value>(observations[0])
        assertContentEquals(byteArrayOf(0x00, 72), (observations[0] as Observation.Value).data)
    }

    @Test
    fun observeValuesReturnsRawBytes() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true)
                    onObserve {
                        flow {
                            emit(byteArrayOf(0x00, 72))
                            emit(byteArrayOf(0x00, 80))
                        }
                    }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        val values = peripheral.observeValues(char, BackpressureStrategy.Unbounded).toList()

        assertEquals(2, values.size)
        assertContentEquals(byteArrayOf(0x00, 72), values[0])
        assertContentEquals(byteArrayOf(0x00, 80), values[1])
    }

    @Test
    fun requestMtuUpdatesMwvl() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") { properties(read = true) }
            }
        }
        peripheral.connect()

        assertEquals(20, peripheral.maximumWriteValueLength.value)
        peripheral.requestMtu(185)
        assertEquals(182, peripheral.maximumWriteValueLength.value) // 185 - 3
    }
}
