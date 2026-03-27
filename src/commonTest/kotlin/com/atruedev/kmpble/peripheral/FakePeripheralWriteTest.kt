package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralWriteTest {
    @Test
    fun writeInvokesHandlerWithCorrectDataAndWriteType() =
        runTest {
            var writtenData: ByteArray? = null
            var writtenType: WriteType? = null

            val peripheral =
                FakePeripheral {
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
            peripheral.write(char, byteArrayOf(0x01, 0x02), WriteType.WithoutResponse)

            assertContentEquals(byteArrayOf(0x01, 0x02), writtenData)
            assertEquals(WriteType.WithoutResponse, writtenType)
        }

    @Test
    fun writeWithoutHandlerSucceedsSilentlyWhenWritable() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(write = true)
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
            peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
        }

    @Test
    fun writeWithoutHandlerSucceedsForWriteWithoutResponse() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(writeWithoutResponse = true)
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
            peripheral.write(char, byteArrayOf(0x01), WriteType.WithoutResponse)
        }

    @Test
    fun writeToNonWritableCharacteristicThrows() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            val ex =
                assertFailsWith<BleException> {
                    peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
                }
            assertIs<GattError>(ex.error)
            assertEquals(GattStatus.WriteNotPermitted, ex.error.status)
        }
}
