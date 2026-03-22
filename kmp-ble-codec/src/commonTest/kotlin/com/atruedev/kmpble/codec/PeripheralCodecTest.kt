package com.atruedev.kmpble.codec

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class PeripheralCodecTest {

    @Test
    fun readDecodesCharacteristic() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(read = true)
                    onRead { "hello".encodeToByteArray() }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        assertEquals("hello", peripheral.read(char, TestStringDecoder))
    }

    @Test
    fun writeEncodesValue() = runTest {
        var writtenData: ByteArray? = null

        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a39") {
                    properties(write = true)
                    onWrite { data, _ -> writtenData = data }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
        peripheral.write(char, "hello", TestStringEncoder)
        assertContentEquals("hello".encodeToByteArray(), writtenData)
    }

    @Test
    fun writePassesWriteType() = runTest {
        var writtenType: WriteType? = null

        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a39") {
                    properties(write = true, writeWithoutResponse = true)
                    onWrite { _, type -> writtenType = type }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
        peripheral.write(char, "hello", TestStringEncoder, WriteType.WithoutResponse)
        assertEquals(WriteType.WithoutResponse, writtenType)
    }

    @Test
    fun writeDefaultsToWithResponse() = runTest {
        var writtenType: WriteType? = null

        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a39") {
                    properties(write = true)
                    onWrite { _, type -> writtenType = type }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
        peripheral.write(char, "hello", TestStringEncoder)
        assertEquals(WriteType.WithResponse, writtenType)
    }

    @Test
    fun observeValuesDecodesStream() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true)
                    onObserve {
                        flow {
                            emit("alpha".encodeToByteArray())
                            emit("beta".encodeToByteArray())
                        }
                    }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        val values = peripheral.observeValues(char, TestStringDecoder, BackpressureStrategy.Unbounded).toList()
        assertEquals(listOf("alpha", "beta"), values)
    }

    @Test
    fun observeDecodesValuesAndPreservesDisconnects() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true)
                    onObserve {
                        flow { emit("data".encodeToByteArray()) }
                    }
                }
            }
        }
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
        val observations = peripheral.observe(char, TestStringDecoder, BackpressureStrategy.Unbounded).toList()

        assertEquals(1, observations.size)
        assertIs<DecodedObservation.Value<String>>(observations[0])
        assertEquals("data", (observations[0] as DecodedObservation.Value).value)
    }
}
