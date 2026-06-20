package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuTransportConfig
import com.atruedev.kmpble.dfu.protocol.DfuOpcode
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DfuTransportTest {

    // --- GattDfuTransport ---

    @Test
    fun `gatt transport resolves control point and data packet characteristics`() = runTest {
        val peripheral = FakePeripheral {
            service(DfuUuids.DFU_SERVICE) {
                characteristic(DfuUuids.DFU_CONTROL_POINT) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
                characteristic(DfuUuids.DFU_PACKET) {
                    properties(writeWithoutResponse = true)
                    onWrite { _, _ -> }
                }
            }
        }

        peripheral.connect()

        // Constructor should resolve characteristics without throwing
        val transport = GattDfuTransport(peripheral, commandTimeout = 10.seconds)

        // verify MTU is exposed
        assertTrue(transport.mtu > 0)

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `gatt transport throws CharacteristicNotFound when DFU service is missing`() = runTest {
        val peripheral = FakePeripheral { /* empty - no DFU service */ }
        peripheral.connect()

        assertFailsWith<DfuError.CharacteristicNotFound> {
            GattDfuTransport(peripheral, commandTimeout = 10.seconds)
        }

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `gatt transport sendCommand writes to control point and returns response`() = runTest {
        // Queue the notification response BEFORE writing, because the notification
        // is consumed from a produceIn channel inside sendCommandViaGatt
        val peripheral = FakePeripheral {
            service(DfuUuids.DFU_SERVICE) {
                characteristic(DfuUuids.DFU_CONTROL_POINT) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve {
                        flow {
                            emit(byteArrayOf(0x60, 0x01, 0x01)) // Select response
                        }
                    }
                }
                characteristic(DfuUuids.DFU_PACKET) {
                    properties(writeWithoutResponse = true)
                    onWrite { _, _ -> }
                }
            }
        }

        peripheral.connect()
        val transport = GattDfuTransport(peripheral, commandTimeout = 10.seconds)

        val command = byteArrayOf(DfuOpcode.SELECT.toByte(), 0x01)
        val response = transport.sendCommand(command)

        // Verify response matches the notification content
        assertContentEquals(byteArrayOf(0x60, 0x01, 0x01), response)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `gatt transport sendData writes to data packet characteristic`() = runTest {
        val writeLog = mutableListOf<ByteArray>()

        val peripheral = FakePeripheral {
            service(DfuUuids.DFU_SERVICE) {
                characteristic(DfuUuids.DFU_CONTROL_POINT) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
                characteristic(DfuUuids.DFU_PACKET) {
                    properties(writeWithoutResponse = true)
                    onWrite { data, _ -> writeLog.add(data.copyOf()) }
                }
            }
        }

        peripheral.connect()
        val transport = GattDfuTransport(peripheral, commandTimeout = 10.seconds)

        val dataPacket = byteArrayOf(0x01, 0x02, 0x03)
        transport.sendData(dataPacket)

        // Data write is asynchronous (WriteType.WithoutResponse), so advance time
        advanceUntilIdle()

        assertEquals(1, writeLog.size)
        assertContentEquals(dataPacket, writeLog[0])

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    // --- SmpTransport ---

    @Test
    fun `smp transport resolves SMP characteristic`() = runTest {
        val peripheral = FakePeripheral {
            service(SmpUuids.SMP_SERVICE) {
                characteristic(SmpUuids.SMP_CHARACTERISTIC) {
                    properties(writeWithoutResponse = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        val transport = SmpTransport(peripheral, commandTimeout = 10.seconds)

        assertTrue(transport.mtu > 0)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `smp transport throws ServiceNotFound when SMP service is missing`() = runTest {
        val peripheral = FakePeripheral { /* empty */ }
        peripheral.connect()

        assertFailsWith<DfuError.ServiceNotFound> {
            SmpTransport(peripheral, commandTimeout = 10.seconds)
        }

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `smp transport sendCommand returns non-fragmented response`() = runTest {
        val smpResponse = byteArrayOf(
            0x00, 0x01, 0x00, 0x04,  // header: length 4
            0x00, 0x00, 0x00, 0x00,  // flags
            0x0A, 0x0B, 0x0C, 0x0D,  // payload (4 bytes)
        )

        val peripheral = FakePeripheral {
            service(SmpUuids.SMP_SERVICE) {
                characteristic(SmpUuids.SMP_CHARACTERISTIC) {
                    properties(writeWithoutResponse = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve {
                        flow {
                            emit(smpResponse)
                        }
                    }
                }
            }
        }

        peripheral.connect()
        val transport = SmpTransport(peripheral, commandTimeout = 10.seconds)

        val cmd = byteArrayOf(0x00, 0x01)
        val response = transport.sendCommand(cmd)

        assertContentEquals(smpResponse, response)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `smp transport sendCommand reassembles fragmented response`() = runTest {
        // SMP response with 20-byte payload, fragmented across 3 notifications
        val expectedPayload = ByteArray(20) { (it + 1).toByte() }
        val smpHeader = byteArrayOf(
            0x00, 0x01, 0x00, 0x14,  // header: length 20
            0x00, 0x00, 0x00, 0x00,  // flags
        )
        val fullResponse = smpHeader + expectedPayload

        // Fragment into 3 pieces: 8 (header) + 8 + 8 + 4
        val fragment1 = fullResponse.copyOfRange(0, 16)
        val fragment2 = fullResponse.copyOfRange(16, 24)
        val fragment3 = fullResponse.copyOfRange(24, 28)

        val peripheral = FakePeripheral {
            service(SmpUuids.SMP_SERVICE) {
                characteristic(SmpUuids.SMP_CHARACTERISTIC) {
                    properties(writeWithoutResponse = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve {
                        flow {
                            emit(fragment1)
                            emit(fragment2)
                            emit(fragment3)
                        }
                    }
                }
            }
        }

        peripheral.connect()
        val transport = SmpTransport(peripheral, commandTimeout = 10.seconds)

        val cmd = byteArrayOf(0x00, 0x01)
        val response = transport.sendCommand(cmd)

        assertContentEquals(fullResponse, response)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `smp transport sendData writes to SMP characteristic`() = runTest {
        val writeLog = mutableListOf<ByteArray>()

        val peripheral = FakePeripheral {
            service(SmpUuids.SMP_SERVICE) {
                characteristic(SmpUuids.SMP_CHARACTERISTIC) {
                    properties(writeWithoutResponse = true, notify = true)
                    onWrite { data, _ -> writeLog.add(data.copyOf()) }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        val transport = SmpTransport(peripheral, commandTimeout = 10.seconds)

        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        transport.sendData(data)
        advanceUntilIdle()

        assertEquals(1, writeLog.size)
        assertContentEquals(data, writeLog[0])

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    // --- EspOtaTransport ---

    @Test
    fun `esp ota transport resolves default characteristics`() = runTest {
        val peripheral = FakePeripheral {
            service(EspOtaUuids.OTA_SERVICE) {
                characteristic(EspOtaUuids.OTA_CONTROL) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
                characteristic(EspOtaUuids.OTA_DATA) {
                    properties(writeWithoutResponse = true)
                    onWrite { _, _ -> }
                }
            }
        }

        peripheral.connect()
        val config = DfuTransportConfig.EspOta()
        val transport = EspOtaTransport(peripheral, config, commandTimeout = 10.seconds)

        assertTrue(transport.mtu > 0)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `esp ota transport throws CharacteristicNotFound when service is missing`() = runTest {
        val peripheral = FakePeripheral { /* empty */ }
        peripheral.connect()
        val config = DfuTransportConfig.EspOta()

        assertFailsWith<DfuError.CharacteristicNotFound> {
            EspOtaTransport(peripheral, config, commandTimeout = 10.seconds)
        }

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `esp ota transport sendCommand writes to control and returns notification`() = runTest {
        val expectedResponse = byteArrayOf(0x00, 0x01, 0x02)

        val peripheral = FakePeripheral {
            service(EspOtaUuids.OTA_SERVICE) {
                characteristic(EspOtaUuids.OTA_CONTROL) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve {
                        flow {
                            emit(expectedResponse)
                        }
                    }
                }
                characteristic(EspOtaUuids.OTA_DATA) {
                    properties(writeWithoutResponse = true)
                    onWrite { _, _ -> }
                }
            }
        }

        peripheral.connect()
        val config = DfuTransportConfig.EspOta()
        val transport = EspOtaTransport(peripheral, config, commandTimeout = 10.seconds)

        val command = byteArrayOf(0x01)
        val response = transport.sendCommand(command)

        assertContentEquals(expectedResponse, response)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `esp ota transport sendData writes to data characteristic`() = runTest {
        val writeLog = mutableListOf<ByteArray>()

        val peripheral = FakePeripheral {
            service(EspOtaUuids.OTA_SERVICE) {
                characteristic(EspOtaUuids.OTA_CONTROL) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
                characteristic(EspOtaUuids.OTA_DATA) {
                    properties(writeWithoutResponse = true)
                    onWrite { data, _ -> writeLog.add(data.copyOf()) }
                }
            }
        }

        peripheral.connect()
        val config = DfuTransportConfig.EspOta()
        val transport = EspOtaTransport(peripheral, config, commandTimeout = 10.seconds)

        val firmwareChunk = byteArrayOf(0x10, 0x20, 0x30)
        transport.sendData(firmwareChunk)
        advanceUntilIdle()

        assertEquals(1, writeLog.size)
        assertContentEquals(firmwareChunk, writeLog[0])

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `esp ota transport uses custom UUIDs when provided`() = runTest {
        val customService = Uuid.parse("a0000000-0000-0000-0000-000000000001")
        val customControl = Uuid.parse("a0000000-0000-0000-0000-000000000002")
        val customData = Uuid.parse("a0000000-0000-0000-0000-000000000003")

        val peripheral = FakePeripheral {
            service(customService) {
                characteristic(customControl) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
                characteristic(customData) {
                    properties(writeWithoutResponse = true)
                    onWrite { _, _ -> }
                }
            }
        }

        peripheral.connect()
        val config = DfuTransportConfig.EspOta(
            serviceUuid = customService,
            controlUuid = customControl,
            dataUuid = customData,
        )
        val transport = EspOtaTransport(peripheral, config, commandTimeout = 10.seconds)

        // Should not throw - characteristic resolution succeeded
        assertTrue(transport.mtu > 0)

        transport.close()
        peripheral.disconnect()
        peripheral.close()
    }
}
