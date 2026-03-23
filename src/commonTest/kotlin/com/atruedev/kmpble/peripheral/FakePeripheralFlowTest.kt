package com.atruedev.kmpble.peripheral

import app.cash.turbine.test
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralFlowTest {
    @Test
    fun observeOnFailedCharacteristicEmitsError() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(notify = true)
                            failWith(GattError("observe", GattStatus.InsufficientAuthentication))
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
            peripheral.observe(char, BackpressureStrategy.Unbounded).test {
                val error = awaitError()
                assertIs<BleException>(error)
            }
        }

    @Test
    fun observeValuesOnFailedCharacteristicEmitsError() =
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
            peripheral.observeValues(char, BackpressureStrategy.Unbounded).test {
                val error = awaitError()
                assertIs<BleException>(error)
            }
        }

    @Test
    fun writeTriggersNotificationViaTurbine() =
        runTest {
            val notifications = MutableSharedFlow<ByteArray>(replay = 1)

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(write = true, notify = true)
                            onWrite { data, _ ->
                                if (data[0] == 0x01.toByte()) {
                                    notifications.emit(byteArrayOf(0x00, 75))
                                }
                            }
                            onObserve { notifications }
                        }
                    }
                }
            peripheral.connect()

            val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a39"))!!

            peripheral.observeValues(char, BackpressureStrategy.Unbounded).test {
                peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
                assertContentEquals(byteArrayOf(0x00, 75), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun controlPointWriteTriggersNotificationOnMeasurementCharacteristic() =
        runTest {
            val notifications = MutableSharedFlow<ByteArray>(replay = 1)
            var deviceState = byteArrayOf(0x00)

            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a39") {
                            properties(write = true)
                            onWrite { data, _ ->
                                if (data[0] == 0x01.toByte()) {
                                    deviceState = byteArrayOf(0x01)
                                    notifications.emit(byteArrayOf(0x00, 75))
                                }
                            }
                        }
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

            peripheral.observe(measureChar, BackpressureStrategy.Unbounded).test {
                peripheral.write(controlChar, byteArrayOf(0x01), WriteType.WithResponse)

                val item = awaitItem()
                assertIs<Observation.Value>(item)
                assertContentEquals(byteArrayOf(0x00, 75), item.data)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
