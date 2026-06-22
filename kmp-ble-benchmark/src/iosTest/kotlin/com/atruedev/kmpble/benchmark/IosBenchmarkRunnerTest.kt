package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * iOS benchmark runner.
 *
 * Executes the full [BleBenchmark] API on iOS Simulator to validate
 * benchmark correctness and result consistency.
 *
 * Run: ./gradlew :kmp-ble-benchmark:iosSimulatorArm64Test --tests "*IosBenchmarkRunner*"
 */
@OptIn(ExperimentalBleApi::class)
class IosBenchmarkRunnerTest {
    private val benchmark = BleBenchmark()

    @Test
    fun `full benchmark suite completes on iOS`() =
        runTest {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        service("180a") {
                            characteristic("2a29") {
                                properties(read = true, write = true)
                                onRead { byteArrayOf(0x4E, 0x6F, 0x75, 0x73) }
                            }
                        }
                    }.build()

            // Connect
            val connResult = benchmark.benchmarkConnection(peripheral, ConnectionOptions())
            assertTrue(connResult.success, "Connect failed: ${connResult.errorMessage}")

            // Discover
            val (discResult, services) = benchmark.benchmarkDiscovery(peripheral)
            assertTrue(discResult.success, "Discovery failed: ${discResult.errorMessage}")
            assertEquals(1, services.size)

            // Read
            val char =
                peripheral.findCharacteristic(
                    serviceUuid = uuidFrom("180a"),
                    characteristicUuid = uuidFrom("2a29"),
                )!!
            val readResult = benchmark.benchmarkGattRead(peripheral, char)
            assertTrue(readResult.success, "Read failed: ${readResult.errorMessage}")

            // Write
            val writeResult =
                benchmark.benchmarkGattWrite(
                    peripheral,
                    char,
                    byteArrayOf(0x01),
                    WriteType.WithResponse,
                )
            assertTrue(writeResult.success, "Write failed: ${writeResult.errorMessage}")

            // Throughput
            val channel = FakeL2capChannel(psm = 0x25, mtu = 512)
            val tpResult = benchmark.benchmarkThroughput(channel, dataSize = 4096)
            assertEquals(4096L, tpResult.totalBytes)
            assertTrue(tpResult.bytesPerSecond >= 0.0)

            peripheral.close()
            channel.close()
        }

    @Test
    fun `latency tracker computes correct statistics on iOS`() =
        runTest {
            val tracker = LatencyTracker()
            tracker.measure { /* no-op */ }
            tracker.measure { /* no-op */ }

            val stats = tracker.summarize("ios-latency")
            assertEquals(2, stats.count)
            assertTrue(stats.min.inWholeNanoseconds >= 0)
        }

    @Test
    fun `throughput meter accumulates correctly on iOS`() {
        val meter = ThroughputMeter()
        meter.start()
        meter.record(1024)
        meter.record(2048)
        val result = meter.stop("ios-throughput")

        assertEquals(3072L, result.totalBytes)
        assertEquals(2L, result.sampleCount)
    }
}
