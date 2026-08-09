package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.error.PeripheralTimeout
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class BatchReadTest {
    private fun createPeripheral(): FakePeripheral =
        FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(read = true)
                    onRead { byteArrayOf(0x00, 0x01) }
                }
                characteristic("2a38") {
                    properties(read = true)
                    onRead { byteArrayOf(0x00, 0x02) }
                }
            }
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf(0x00, 0x03) }
                }
            }
        }

    private suspend fun FakePeripheral.connectedCharacteristics(): List<Characteristic> {
        connect()
        return services.value!!.flatMap { it.characteristics }
    }

    @Test
    fun batchReadReturnsAllValues() =
        runTest {
            val peripheral = createPeripheral()
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            assertEquals(3, results.size)
            for ((char, result) in results) {
                assertNotNull(result.getOrNull(), "read should succeed for $char")
            }
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun batchReadMapsCorrectValuesToCharacteristics() =
        runTest {
            val peripheral = createPeripheral()
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            val byUuid = results.entries.associate { (char, result) -> char.uuid to result.getOrThrow() }
            assertEquals(byteArrayOf(0x00, 0x01).toList(), byUuid[uuidFrom("2a37")]!!.toList())
            assertEquals(byteArrayOf(0x00, 0x02).toList(), byUuid[uuidFrom("2a38")]!!.toList())
            assertEquals(byteArrayOf(0x00, 0x03).toList(), byUuid[uuidFrom("2a19")]!!.toList())
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun batchReadIsolatesFailures() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead { byteArrayOf(0x00, 0x01) }
                        }
                        characteristic("2a38") {
                            properties(read = true)
                            onRead { throw BleException(GattError("read", GattStatus.Failure)) }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            assertEquals(2, results.size)
            assertEquals(1, results.values.count { it.isSuccess })
            assertEquals(1, results.values.count { it.isFailure })
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun gattErrorIsPreservedAsBleException() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead { throw BleException(GattError("read", GattStatus.Failure)) }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            val failure = results.values.single().exceptionOrNull()
            assertIs<BleException>(failure)
            assertIs<GattError>(failure.error)
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun disconnectMapsToConnectionLost() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead { throw NotConnectedException() }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            val failure = results.values.single().exceptionOrNull()
            assertIs<BleException>(failure)
            assertIs<ConnectionLost>(failure.error)
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun unexpectedExceptionMapsToOperationFailed() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead { throw RuntimeException("boom") }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            val failure = results.values.single().exceptionOrNull()
            assertIs<BleException>(failure)
            assertIs<OperationFailed>(failure.error)
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun slowReadStillCompletesSequentially() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead {
                                delay(100)
                                byteArrayOf(0x01)
                            }
                        }
                        characteristic("2a38") {
                            properties(read = true)
                            onRead { byteArrayOf(0x02) }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            assertEquals(2, results.size)
            assertTrue(results.values.all { it.isSuccess }, "both reads should succeed")
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun cancellationPropagatesInsteadOfBecomingFailure() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead {
                                delay(10_000)
                                byteArrayOf(0x01)
                            }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()

            val result =
                runCatching {
                    coroutineScope {
                        val job = async { peripheral.batchRead(chars) }
                        yield()
                        job.cancel()
                        job.await()
                    }
                }
            assertTrue(result.isFailure)
            assertIs<CancellationException>(result.exceptionOrNull())
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun queueTimeoutMapsToPeripheralTimeout() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead {
                                // TimeoutCancellationException constructor is internal;
                                // produce a real one via withTimeout.
                                withTimeout(1) { delay(100) }
                                byteArrayOf(0x01)
                            }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            val failure = results.values.single().exceptionOrNull()
            assertIs<BleException>(failure)
            assertIs<PeripheralTimeout>(failure.error)
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun timeoutOnOneCharacteristicDoesNotAbortLaterReads() =
        runTest {
            val peripheral =
                FakePeripheral {
                    service("180d") {
                        characteristic("2a37") {
                            properties(read = true)
                            onRead {
                                withTimeout(1) { delay(100) }
                                byteArrayOf(0x01)
                            }
                        }
                        characteristic("2a38") {
                            properties(read = true)
                            onRead { byteArrayOf(0x02) }
                        }
                    }
                }
            val chars = peripheral.connectedCharacteristics()
            val results = peripheral.batchRead(chars)

            assertEquals(2, results.size)
            val timedOut = results.values.first { it.isFailure }
            val exception = assertIs<BleException>(timedOut.exceptionOrNull())
            assertIs<PeripheralTimeout>(exception.error)
            assertTrue(results.values.any { it.isSuccess }, "later read should still succeed")
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun batchReadRejectsEmptyList() =
        runTest {
            val peripheral = createPeripheral()
            assertFailsWith<IllegalArgumentException> {
                peripheral.batchRead(emptyList())
            }
        }

    @Test
    fun batchReadRejectsDuplicateCharacteristics() =
        runTest {
            val peripheral = createPeripheral()
            val chars = peripheral.connectedCharacteristics()
            val char = chars.first()

            assertFailsWith<IllegalArgumentException> {
                peripheral.batchRead(listOf(char, char))
            }
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun batchReadWithSingleCharacteristic() =
        runTest {
            val peripheral = createPeripheral()
            val chars = peripheral.connectedCharacteristics()
            val char = chars.first()

            val results = peripheral.batchRead(listOf(char))

            assertEquals(1, results.size)
            assertTrue(results[char]!!.isSuccess)
            peripheral.disconnect()
            peripheral.close()
        }
}
