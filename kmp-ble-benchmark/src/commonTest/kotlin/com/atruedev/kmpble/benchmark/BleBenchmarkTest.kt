package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalBleApi::class)
class BleBenchmarkTest {
    private val benchmark = BleBenchmark()

    @Test
    fun `benchmarkConnection returns result when connection succeeds`() =
        runTest {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(read = true) }
                        }
                    }.build()

            val result =
                benchmark.benchmarkConnection(
                    peripheral,
                    ConnectionOptions(timeouts = OperationTimeouts(connect = 5.seconds)),
                )

            assertTrue(result.success, "Expected successful connection but got: ${result.errorMessage}")
            assertTrue(result.elapsed.inWholeNanoseconds >= 0, "Elapsed time should be non-negative")
            peripheral.close()
        }

    @Test
    fun `benchmarkGattRead returns elapsed time for read`() =
        runTest {
            val expectedData = byteArrayOf(0x06, 0x42)
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180d") {
                            characteristic("2a37") {
                                properties(read = true)
                                onRead { expectedData }
                            }
                        }
                    }.build()

            peripheral.connect()
            val char =
                peripheral.findCharacteristic(
                    serviceUuid = uuidFrom("180d"),
                    characteristicUuid = uuidFrom("2a37"),
                )!!

            val result = benchmark.benchmarkGattRead(peripheral, char)

            assertTrue(result.success, "Expected successful read but got: ${result.errorMessage}")
            assertTrue(result.elapsed.inWholeNanoseconds >= 0, "Elapsed time should be non-negative")
            peripheral.close()
        }

    @Test
    fun `benchmarkGattWrite returns elapsed time for write`() =
        runTest {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(write = true) }
                        }
                    }.build()

            peripheral.connect()
            val char =
                peripheral.findCharacteristic(
                    serviceUuid = uuidFrom("180d"),
                    characteristicUuid = uuidFrom("2a37"),
                )!!

            val result =
                benchmark.benchmarkGattWrite(
                    peripheral,
                    char,
                    byteArrayOf(0x01),
                    WriteType.WithResponse,
                )

            assertTrue(result.success, "Expected successful write but got: ${result.errorMessage}")
            assertTrue(result.elapsed.inWholeNanoseconds >= 0, "Elapsed time should be non-negative")
            peripheral.close()
        }

    @Test
    fun `benchmarkDiscovery returns services and elapsed time`() =
        runTest {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180d") { characteristic("2a37") { properties(read = true) } }
                        service("180f") { characteristic("2a19") { properties(read = true) } }
                    }.build()

            peripheral.connect()
            val (result, services) = benchmark.benchmarkDiscovery(peripheral)

            assertTrue(result.success, "Expected successful discovery but got: ${result.errorMessage}")
            assertEquals(2, services.size, "Should discover both services")
            assertTrue(result.elapsed.inWholeNanoseconds >= 0, "Elapsed time should be non-negative")
            peripheral.close()
        }

    @Test
    fun `benchmarkThroughput returns result for L2CAP writes`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25, mtu = 512)

            val result = benchmark.benchmarkThroughput(channel, dataSize = 4096)

            assertEquals(4096L, result.totalBytes, "Should transfer all bytes")
            assertTrue(result.bytesPerSecond >= 0.0, "Throughput should be non-negative")
            channel.close()
        }

    @Test
    fun `benchmarkResult with failure captures error message`() {
        val result =
            BenchmarkResult(
                elapsed = 100.milliseconds,
                success = false,
                errorMessage = "Timeout",
            )

        assertEquals(false, result.success)
        assertEquals("Timeout", result.errorMessage)
    }

    @Test
    fun `throughputResult computes bytesPerSecond correctly`() {
        val result =
            ThroughputResult(
                label = "test",
                totalBytes = 1000L,
                sampleCount = 10,
                duration = 1.seconds,
            )

        assertEquals(1000L, result.totalBytes)
        assertEquals(1000.0, result.bytesPerSecond)
    }
}
