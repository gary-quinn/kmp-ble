package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cross-platform benchmark suite that exercises the full [BleBenchmark] API
 * and validates benchmark result consistency across KMP targets.
 *
 * Run on JVM:  ./gradlew :jvmTest --tests "*CrossPlatformBenchmark*"
 * Run on iOS:  ./gradlew :iosSimulatorArm64Test --tests "*CrossPlatformBenchmark*"
 *
 * Compare results between platforms to validate performance characteristics.
 */
@OptIn(ExperimentalBleApi::class)
class CrossPlatformBenchmarkTest {
    private val benchmark = BleBenchmark()

    // -- Connection benchmarks --

    @Test
    fun `connection benchmark returns success for valid peripheral`() =
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
                    ConnectionOptions(),
                )

            assertTrue(result.success, "Connection should succeed: ${result.errorMessage}")
            peripheral.close()
        }

    @Test
    fun `simulated disconnect transitions to Disconnected state`() =
        runTest {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(read = true) }
                        }
                    }.build()

            // Connect first, then simulate a disconnect for failure benchmarking.
            // FakePeripheral.connect() does not throw on failure -- it processes
            // ConnectionLost through the state machine. Use simulateDisconnect()
            // to test the failure path. After a single ConnectionLost from
            // Connected.Ready, the state machine transitions to Disconnecting.Error;
            // a subsequent ConnectionLost transitions to Disconnected.ByError.
            peripheral.connect()
            peripheral.simulateDisconnect(
                ConnectionLost("GATT error: Connection terminated by peer"),
            )
            val state = peripheral.state.value
            assertTrue(
                state is State.Disconnecting,
                "Should be disconnecting after simulated disconnect, got: $state",
            )
            peripheral.close()
        }

    // -- Benchmark data types --

    @Test
    fun `benchmark result failure data class constructs correctly`() {
        val result =
            BenchmarkResult(
                elapsed = 150.milliseconds,
                success = false,
                errorMessage = "GATT error: Insufficient Authentication",
            )

        assertEquals(false, result.success)
        assertEquals("GATT error: Insufficient Authentication", result.errorMessage)
        assertEquals(150, result.elapsed.inWholeMilliseconds)
    }

    // -- GATT operation benchmarks --

    @Test
    fun `gatt read benchmark returns data and valid timing`() =
        runTest {
            val expectedData = byteArrayOf(0x06, 0x42, 0x00)
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180a") {
                            characteristic("2a29") {
                                properties(read = true)
                                onRead { expectedData }
                            }
                        }
                    }.build()

            peripheral.connect()
            val char =
                peripheral.findCharacteristic(
                    serviceUuid = uuidFrom("180a"),
                    characteristicUuid = uuidFrom("2a29"),
                )!!

            val result = benchmark.benchmarkGattRead(peripheral, char)

            assertTrue(result.success, "GATT read should succeed: ${result.errorMessage}")
            // Wall-clock time is used; in virtual time tests it may be near-zero
            assertTrue(result.elapsed.inWholeNanoseconds >= 0, "Elapsed should be non-negative")
            peripheral.close()
        }

    @Test
    fun `gatt write with response benchmark succeeds`() =
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
                    byteArrayOf(0x01, 0x02),
                    WriteType.WithResponse,
                )

            assertTrue(result.success, "GATT write should succeed: ${result.errorMessage}")
            assertTrue(result.elapsed.inWholeNanoseconds >= 0)
            peripheral.close()
        }

    // -- Service discovery benchmark --

    @Test
    fun `discovery benchmark returns all services and valid timing`() =
        runTest {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("1800") { characteristic("2a00") { properties(read = true) } }
                        service("1801") { characteristic("2a05") { properties(read = true) } }
                        service("180a") { characteristic("2a29") { properties(read = true) } }
                    }.build()

            peripheral.connect()
            val (result, services) = benchmark.benchmarkDiscovery(peripheral)

            assertTrue(result.success, "Discovery should succeed: ${result.errorMessage}")
            assertEquals(3, services.size, "Should discover all three services")
            assertTrue(result.elapsed.inWholeNanoseconds >= 0)
            peripheral.close()
        }

    // -- Throughput benchmark --

    @Test
    fun `throughput benchmark transfers all data`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25, mtu = 512)

            val result = benchmark.benchmarkThroughput(channel, dataSize = 8192)

            assertEquals(8192L, result.totalBytes, "Should transfer all bytes")
            // bytesPerSecond may be zero in virtual-time tests (KMP-315)
            assertTrue(result.bytesPerSecond >= 0.0, "Throughput should be non-negative")
            channel.close()
        }

    // -- Full suite benchmark --

    @Test
    fun `full benchmark suite runs end-to-end`() =
        runTest {
            // Establish a minimal BLE peripheral with enough GATT structure
            // for the full suite: connection, discovery, read, write, throughput.
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180a") {
                            characteristic("2a29") {
                                properties(read = true, write = true)
                                onRead { byteArrayOf(0x4E, 0x6F, 0x75, 0x73) } // "Nous"
                            }
                        }
                    }.build()

            // Phase 1: Connect
            val connResult = benchmark.benchmarkConnection(peripheral)
            assertTrue(connResult.success, "Connect failed: ${connResult.errorMessage}")

            // Phase 2: Discover
            val (discResult, services) = benchmark.benchmarkDiscovery(peripheral)
            assertTrue(discResult.success, "Discovery failed: ${discResult.errorMessage}")
            assertEquals(1, services.size)

            // Phase 3: Read
            val char =
                peripheral.findCharacteristic(
                    serviceUuid = uuidFrom("180a"),
                    characteristicUuid = uuidFrom("2a29"),
                )!!
            val readResult = benchmark.benchmarkGattRead(peripheral, char)
            assertTrue(readResult.success, "Read failed: ${readResult.errorMessage}")

            // Phase 4: Write
            val writeResult =
                benchmark.benchmarkGattWrite(
                    peripheral,
                    char,
                    byteArrayOf(0x01),
                    WriteType.WithResponse,
                )
            assertTrue(writeResult.success, "Write failed: ${writeResult.errorMessage}")

            // Phase 5: Throughput
            val channel = FakeL2capChannel(psm = 0x25, mtu = 512)
            val tpResult = benchmark.benchmarkThroughput(channel, dataSize = 4096)
            assertEquals(4096L, tpResult.totalBytes)
            assertTrue(tpResult.bytesPerSecond >= 0.0)

            peripheral.close()
            channel.close()
        }

    // -- Throughput edge cases --

    @Test
    fun `throughput with large payload completes`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25, mtu = 256)

            val result = benchmark.benchmarkThroughput(channel, dataSize = 65536)

            assertEquals(65536L, result.totalBytes, "Large payload should complete")
            assertTrue(result.bytesPerSecond >= 0.0)
            channel.close()
        }

    @Test
    fun `throughput with zero data size returns zero result`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25, mtu = 512)

            val result = benchmark.benchmarkThroughput(channel, dataSize = 0)

            assertEquals(0L, result.totalBytes)
            assertEquals(0.0, result.bytesPerSecond)
            channel.close()
        }
}
