package com.atruedev.kmpble.benchmark

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Benchmark metrics for a single BLE operation with success/failure tracking.
 *
 * Unlike [TimingResult] (which always wraps a value), this type captures
 * both successful and failed operations for reliability benchmarking.
 *
 * @property elapsed Wall-clock time for the operation.
 * @property success Whether the operation completed without error.
 * @property errorMessage Description of the failure, if any.
 */
public data class BenchmarkResult(
    val elapsed: Duration,
    val success: Boolean,
    val errorMessage: String? = null,
)

/**
 * Lightweight benchmark utility for measuring BLE operation latency and throughput.
 *
 * Complements [bleStopwatch], [LatencyTracker], and [ThroughputMeter] with
 * single-shot operation benchmarks that capture success/failure status.
 * Works with both real [Peripheral] instances and [FakePeripheral] for
 * deterministic testing.
 *
 * For multi-sample latency statistics, use [LatencyTracker].
 * For throughput over multiple operations, use [ThroughputMeter].
 *
 * ## Usage
 *
 * ```kotlin
 * val benchmark = BleBenchmark()
 * val result = benchmark.benchmarkConnection(peripheral)
 * println("Connected in ${result.elapsed}")
 * ```
 */
@OptIn(ExperimentalBleApi::class)
public class BleBenchmark(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /**
     * Measure the time to connect to a peripheral.
     *
     * Starts timing before [Peripheral.connect], stops when the connection
     * state reaches [com.atruedev.kmpble.connection.State.Connected].
     *
     * @param peripheral The peripheral to connect to (must be disconnected).
     * @param options Connection configuration.
     * @return [BenchmarkResult] with elapsed connection time.
     */
    public suspend fun benchmarkConnection(
        peripheral: Peripheral,
        options: ConnectionOptions = ConnectionOptions(),
    ): BenchmarkResult {
        val mark = timeSource.markNow()
        return try {
            peripheral.connect(options)
            BenchmarkResult(elapsed = mark.elapsedNow(), success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BenchmarkResult(
                elapsed = mark.elapsedNow(),
                success = false,
                errorMessage = e.message ?: "Connection failed",
            )
        }
    }

    /**
     * Measure the round-trip time for a GATT read operation.
     *
     * Times a single [Peripheral.read] call from invocation to result.
     *
     * @param peripheral A connected peripheral.
     * @param characteristic The characteristic to read.
     * @return [BenchmarkResult] with elapsed read time.
     */
    public suspend fun benchmarkGattRead(
        peripheral: Peripheral,
        characteristic: Characteristic,
    ): BenchmarkResult {
        val mark = timeSource.markNow()
        return try {
            peripheral.read(characteristic)
            BenchmarkResult(elapsed = mark.elapsedNow(), success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BenchmarkResult(
                elapsed = mark.elapsedNow(),
                success = false,
                errorMessage = e.message ?: "Read failed",
            )
        }
    }

    /**
     * Measure the round-trip time for a GATT write operation.
     *
     * Times a single [Peripheral.write] call from invocation to completion.
     *
     * @param peripheral A connected peripheral.
     * @param characteristic The characteristic to write.
     * @param data The data to write.
     * @param writeType Write type (with/without response).
     * @return [BenchmarkResult] with elapsed write time.
     */
    public suspend fun benchmarkGattWrite(
        peripheral: Peripheral,
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType = WriteType.WithResponse,
    ): BenchmarkResult {
        val mark = timeSource.markNow()
        return try {
            peripheral.write(characteristic, data, writeType)
            BenchmarkResult(elapsed = mark.elapsedNow(), success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BenchmarkResult(
                elapsed = mark.elapsedNow(),
                success = false,
                errorMessage = e.message ?: "Write failed",
            )
        }
    }

    /**
     * Measure the time to discover services after connection.
     *
     * Times [Peripheral.refreshServices] from invocation to populated list.
     *
     * @param peripheral A connected peripheral.
     * @return Pair of [BenchmarkResult] and discovered services.
     */
    public suspend fun benchmarkDiscovery(peripheral: Peripheral): Pair<BenchmarkResult, List<DiscoveredService>> {
        val mark = timeSource.markNow()
        return try {
            val services = peripheral.refreshServices()
            Pair(
                BenchmarkResult(elapsed = mark.elapsedNow(), success = true),
                services,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Pair(
                BenchmarkResult(
                    elapsed = mark.elapsedNow(),
                    success = false,
                    errorMessage = e.message ?: "Discovery failed",
                ),
                emptyList(),
            )
        }
    }

    /**
     * Measure L2CAP throughput by writing a payload of [dataSize] bytes.
     *
     * Writes the data in MTU-sized chunks and delegates measurement to a
     * [ThroughputMeter] for consistent statistics across the benchmark package.
     *
     * @param channel An open L2CAP channel.
     * @param dataSize Total bytes to write.
     * @return [ThroughputResult] with bytes transferred, elapsed time, and transfer rate.
     */
    public suspend fun benchmarkThroughput(
        channel: L2capChannel,
        dataSize: Int = 65536,
    ): ThroughputResult {
        val meter = ThroughputMeter(timeSource)
        meter.start()
        val chunk = ByteArray(minOf(dataSize, channel.mtu)) { 0x00 }
        var written = 0L
        while (written < dataSize) {
            val toWrite = minOf(chunk.size.toLong(), dataSize - written).toInt()
            // Only copy a sub-array for the final partial chunk
            val payload = if (toWrite == chunk.size) chunk else chunk.copyOf(toWrite)
            channel.write(payload)
            written += toWrite
            meter.record(toWrite)
        }
        return meter.stop("L2CAP throughput")
    }
}
