package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralEnhancedTest {
    // --- onWrite ---

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
                            // No onWrite handler
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
            // Should not throw
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
                            // read-only, no write property
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            val ex = assertFailsWith<BleException> {
                peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
            }
            assertIs<GattError>(ex.error)
            assertEquals(GattStatus.WriteNotPermitted, (ex.error as GattError).status)
        }

    @Test
    fun writeTriggersNotification() =
        runTest {
            val controlPoint = MutableSharedFlow<ByteArray>(replay = 1)

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(write = true, notify = true)
                            onWrite { data, _ ->
                                if (data[0] == 0x01.toByte()) {
                                    controlPoint.emit(byteArrayOf(0x00, 75))
                                }
                            }
                            onObserve { controlPoint }
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!

            // Write triggers notification (replay=1 ensures the value is buffered)
            peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)

            // Collect the replayed value
            val received = peripheral.observeValues(char, BackpressureStrategy.Unbounded).first()
            assertContentEquals(byteArrayOf(0x00, 75), received)
        }

    // --- respondAfter ---

    @Test
    fun readWithDelayReturnsCorrectValue() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            respondAfter(100.milliseconds)
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
    fun writeWithDelayInvokesHandler() =
        runTest {
            var writtenData: ByteArray? = null

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(write = true)
                            respondAfter(100.milliseconds)
                            onWrite { data, _ -> writtenData = data }
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
            peripheral.write(char, byteArrayOf(0x42), WriteType.WithResponse)
            assertContentEquals(byteArrayOf(0x42), writtenData)
        }

    @Test
    fun cancellationDuringDelayPropagates() =
        runTest {
            var handlerInvoked = false

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            respondAfter(10.seconds)
                            onRead {
                                handlerInvoked = true
                                byteArrayOf(0x00, 72)
                            }
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!

            assertFailsWith<CancellationException> {
                withTimeout(50.milliseconds) {
                    peripheral.read(char)
                }
            }

            assertEquals(false, handlerInvoked)
        }

    @Test
    fun zeroDurationBehavesAsNoDelay() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            respondAfter(0.milliseconds)
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
    fun differentCharacteristicsCanHaveDifferentDelays() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            respondAfter(50.milliseconds)
                            onRead { byteArrayOf(0x01) }
                        }
                        characteristic("2a38") {
                            properties(read = true)
                            respondAfter(100.milliseconds)
                            onRead { byteArrayOf(0x02) }
                        }
                    }
                }
            peripheral.connect()

            val char1 = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            val char2 = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a38"))!!

            assertContentEquals(byteArrayOf(0x01), peripheral.read(char1))
            assertContentEquals(byteArrayOf(0x02), peripheral.read(char2))
        }

    // --- failWith ---

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
            val ex = assertFailsWith<BleException> {
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
            val ex = assertFailsWith<BleException> {
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
            val ex = assertFailsWith<BleException> {
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

    // --- Integration ---

    @Test
    fun writeToControlPointTriggersNotificationAndReadConfirms() =
        runTest {
            val notifications = MutableSharedFlow<ByteArray>(replay = 1)
            var deviceState = byteArrayOf(0x00)

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        // Control point characteristic
                        characteristic("2a39") {
                            properties(write = true)
                            onWrite { data, _ ->
                                if (data[0] == 0x01.toByte()) {
                                    deviceState = byteArrayOf(0x01)
                                    notifications.emit(byteArrayOf(0x00, 75))
                                }
                            }
                        }
                        // Measurement characteristic
                        characteristic("2a37") {
                            properties(read = true, notify = true)
                            onRead { deviceState }
                            onObserve { notifications }
                        }
                    }
                }
            peripheral.connect()

            val controlChar = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!
            val measureChar = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!

            // Write to control point → triggers notification (replay=1 buffers it)
            peripheral.write(controlChar, byteArrayOf(0x01), WriteType.WithResponse)

            // Collect the replayed notification
            val notification = peripheral.observeValues(measureChar, BackpressureStrategy.Unbounded).first()
            assertContentEquals(byteArrayOf(0x00, 75), notification)

            // Verify state change via read
            val state = peripheral.read(measureChar)
            assertContentEquals(byteArrayOf(0x01), state)
        }
}
