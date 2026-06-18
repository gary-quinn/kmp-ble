# Module kmp-ble-benchmark

Performance measurement and telemetry utilities for kmp-ble.

The module provides:

- **BleBenchmark** - One-shot benchmark for BLE operations (connect, read, write, discovery, throughput)
- **LatencyTracker** - Multi-sample latency collection with percentile statistics (p50, p95, p99)
- **ThroughputMeter** - Data throughput measurement over time windows
- **TimingResult / bleStopwatch** - Lightweight single-operation timing

## Usage

```kotlin
val benchmark = BleBenchmark()
val result = benchmark.benchmarkConnection(peripheral)
println("Connected in ${result.elapsed}")
```
