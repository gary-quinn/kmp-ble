package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralDelayTest {
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
}
