package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralErrorInjectionTest {
    @Test
    fun readOnFailedCharacteristicThrows() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            failWith(GattError("read", GattStatus.InsufficientAuthentication))
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            val ex =
                assertFailsWith<BleException> {
                    peripheral.read(char)
                }
            assertIs<GattError>(ex.error)
            assertEquals(GattStatus.InsufficientAuthentication, (ex.error as GattError).status)
        }

    @Test
    fun writeOnFailedCharacteristicThrows() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(write = true)
                            failWith(GattError("write", GattStatus.InsufficientEncryption))
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
            val ex =
                assertFailsWith<BleException> {
                    peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
                }
            assertIs<GattError>(ex.error)
            assertEquals(GattStatus.InsufficientEncryption, (ex.error as GattError).status)
        }

    @Test
    fun observeOnFailedCharacteristicThrows() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(notify = true)
                            failWith(GattError("observe", GattStatus.Failure))
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            assertFailsWith<BleException> {
                peripheral.observe(char, BackpressureStrategy.Unbounded).toList()
            }
        }

    @Test
    fun failWithPlusRespondAfterDelaysThenFails() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            respondAfter(100.milliseconds)
                            failWith(GattError("read", GattStatus.InsufficientAuthentication))
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            val ex =
                assertFailsWith<BleException> {
                    peripheral.read(char)
                }
            assertEquals(GattStatus.InsufficientAuthentication, (ex.error as GattError).status)
        }

    @Test
    fun failWithTakesPrecedenceOverHandlers() =
        runTest {
            var readInvoked = false
            var writeInvoked = false

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true, write = true)
                            failWith(GattError("op", GattStatus.Failure))
                            onRead {
                                readInvoked = true
                                byteArrayOf(0x00)
                            }
                            onWrite { _, _ ->
                                writeInvoked = true
                            }
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!

            assertFailsWith<BleException> { peripheral.read(char) }
            assertFailsWith<BleException> {
                peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
            }

            assertEquals(false, readInvoked)
            assertEquals(false, writeInvoked)
        }
}
