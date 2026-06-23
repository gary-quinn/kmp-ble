package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * GATT write-type conformance tests.
 *
 * Verifies that Write Request (WithResponse), Write Command (WithoutResponse),
 * and Signed Write are correctly delivered to the peripheral handler with the
 * expected write type. Also covers property-based rejection and edge cases.
 */
public abstract class GattWriteTypeConformanceTest : BleConformanceTest() {
    // --- Core write-type delivery ---

    @Test
    fun `write with response delivers correct data and write type to handler`() =
        runTest {
            val writeData = byteArrayOf(0x01, 0x02, 0x03)
            var receivedData: ByteArray? = null
            var receivedType: WriteType? = null

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(write = true)
                            onWrite { data, type ->
                                receivedData = data
                                receivedType = type
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, writeData, WriteType.WithResponse)

            assertContentEquals(
                writeData,
                receivedData!!,
                "Handler must receive the exact data written",
            )
            assertEquals(
                WriteType.WithResponse,
                receivedType,
                "Handler must receive WithResponse write type",
            )
            peripheral.close()
        }

    @Test
    fun `write without response delivers correct data and write type to handler`() =
        runTest {
            val writeData = byteArrayOf(0xA0.toByte(), 0xB1.toByte(), 0xC2.toByte(), 0xD3.toByte())
            var receivedData: ByteArray? = null
            var receivedType: WriteType? = null

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(writeWithoutResponse = true)
                            onWrite { data, type ->
                                receivedData = data
                                receivedType = type
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, writeData, WriteType.WithoutResponse)

            assertContentEquals(
                writeData,
                receivedData!!,
                "Handler must receive the exact data written",
            )
            assertEquals(
                WriteType.WithoutResponse,
                receivedType,
                "Handler must receive WithoutResponse write type",
            )
            peripheral.close()
        }

    @Test
    fun `signed write delivers correct data and write type to handler`() =
        runTest {
            val writeData = byteArrayOf(0xCA.toByte(), 0xFE.toByte())
            var receivedData: ByteArray? = null
            var receivedType: WriteType? = null

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(signedWrite = true)
                            onWrite { data, type ->
                                receivedData = data
                                receivedType = type
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, writeData, WriteType.Signed)

            assertContentEquals(
                writeData,
                receivedData!!,
                "Handler must receive the exact data written",
            )
            assertEquals(
                WriteType.Signed,
                receivedType,
                "Handler must receive Signed write type",
            )
            peripheral.close()
        }

    @Test
    fun `multiple writes to same characteristic deliver distinct data`() =
        runTest {
            val firstData = byteArrayOf(0x01, 0x02)
            val secondData = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
            val receivedDataList = mutableListOf<ByteArray>()
            val receivedTypeList = mutableListOf<WriteType>()

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(write = true, writeWithoutResponse = true)
                            onWrite { data, type ->
                                receivedDataList.add(data)
                                receivedTypeList.add(type)
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, firstData, WriteType.WithResponse)
            peripheral.write(char, secondData, WriteType.WithoutResponse)

            assertEquals(
                2,
                receivedDataList.size,
                "Handler must be invoked for each write",
            )
            assertContentEquals(firstData, receivedDataList[0])
            assertContentEquals(secondData, receivedDataList[1])
            assertEquals(WriteType.WithResponse, receivedTypeList[0])
            assertEquals(WriteType.WithoutResponse, receivedTypeList[1])
            peripheral.close()
        }

    // --- Property-based rejection ---

    @Test
    fun `write without response rejected when property is missing`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        // Only write (WithResponse) property, no writeWithoutResponse
                        characteristic("2a37") { properties(write = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            val exception =
                assertFailsWith<BleException>(
                    "Write without response on write-only characteristic must throw",
                ) {
                    peripheral.write(char, byteArrayOf(0x01), WriteType.WithoutResponse)
                }
            assertEquals(GattStatus.WriteNotPermitted, (exception.error as GattError).status)
            peripheral.close()
        }

    @Test
    fun `write with response rejected when property is missing`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        // Only writeWithoutResponse property, no write
                        characteristic("2a37") {
                            properties(writeWithoutResponse = true)
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            val exception =
                assertFailsWith<BleException>(
                    "Write with response on writeWithoutResponse-only characteristic must throw",
                ) {
                    peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
                }
            assertEquals(GattStatus.WriteNotPermitted, (exception.error as GattError).status)
            peripheral.close()
        }

    @Test
    fun `signed write rejected when property is missing`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        // Only write property, no signedWrite
                        characteristic("2a37") { properties(write = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            val exception =
                assertFailsWith<BleException>(
                    "Signed write on write-only characteristic must throw",
                ) {
                    peripheral.write(char, byteArrayOf(0x01), WriteType.Signed)
                }
            assertEquals(GattStatus.WriteNotPermitted, (exception.error as GattError).status)
            peripheral.close()
        }

    // --- Edge cases ---

    @Test
    fun `empty data write with response succeeds`() =
        runTest {
            var handlerInvoked = false
            var receivedData: ByteArray? = null

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(write = true)
                            onWrite { data, _ ->
                                handlerInvoked = true
                                receivedData = data
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, byteArrayOf(), WriteType.WithResponse)

            assertTrue(
                handlerInvoked,
                "Handler must be invoked for empty data write",
            )
            assertEquals(
                0,
                receivedData!!.size,
                "Empty write must deliver zero-length data",
            )
            peripheral.close()
        }

    @Test
    fun `empty data write without response succeeds`() =
        runTest {
            var handlerInvoked = false

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(writeWithoutResponse = true)
                            onWrite { _, _ -> handlerInvoked = true }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, byteArrayOf(), WriteType.WithoutResponse)

            assertTrue(
                handlerInvoked,
                "Handler must be invoked for empty write without response",
            )
            peripheral.close()
        }

    @Test
    fun `large data write with response is delivered correctly`() =
        runTest {
            // 128 bytes - exceeds common MTU to test chunking path
            val largeData = ByteArray(128) { it.toByte() }
            var receivedData: ByteArray? = null
            var receivedType: WriteType? = null

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(write = true)
                            onWrite { data, type ->
                                receivedData = data
                                receivedType = type
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, largeData, WriteType.WithResponse)

            assertContentEquals(
                largeData,
                receivedData!!,
                "Handler must receive full large data after chunking",
            )
            assertEquals(WriteType.WithResponse, receivedType)
            peripheral.close()
        }

    @Test
    fun `large data write without response is delivered correctly`() =
        runTest {
            // 256 bytes - larger than MTU, tests unacknowledged chunking
            val largeData = ByteArray(256) { (it % 256).toByte() }
            var receivedData: ByteArray? = null
            var receivedType: WriteType? = null

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(writeWithoutResponse = true)
                            onWrite { data, type ->
                                receivedData = data
                                receivedType = type
                            }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, largeData, WriteType.WithoutResponse)

            assertContentEquals(
                largeData,
                receivedData!!,
                "Handler must receive full large data after chunking",
            )
            assertEquals(WriteType.WithoutResponse, receivedType)
            peripheral.close()
        }

    @Test
    fun `write all three types to characteristic with all write properties`() =
        runTest {
            val receivedTypes = mutableListOf<WriteType>()

            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(
                                write = true,
                                writeWithoutResponse = true,
                                signedWrite = true,
                            )
                            onWrite { _, type -> receivedTypes.add(type) }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, byteArrayOf(0x01), WriteType.WithResponse)
            peripheral.write(char, byteArrayOf(0x02), WriteType.WithoutResponse)
            peripheral.write(char, byteArrayOf(0x03), WriteType.Signed)

            assertEquals(
                3,
                receivedTypes.size,
                "All three writes must be delivered to the handler",
            )
            assertEquals(WriteType.WithResponse, receivedTypes[0])
            assertEquals(WriteType.WithoutResponse, receivedTypes[1])
            assertEquals(WriteType.Signed, receivedTypes[2])
            peripheral.close()
        }
}
