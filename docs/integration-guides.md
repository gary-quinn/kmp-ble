# Feature Integration Guides

Production-ready patterns for composing kmp-ble features into resilient BLE workflows.

## Guides

- [Connection Parameter Updates](#connection-parameter-updates)
- [PHY Selection](#phy-selection)
- [Beacon Scanning + Connection Coexistence](#beacon-scanning--connection-coexistence)
- [Periodic Advertising](#periodic-advertising)
- [Extended Advertising](#extended-advertising)
- [Connection Quality Monitoring](#connection-quality-monitoring)
- [LE Power Control and Path Loss](#le-power-control-and-path-loss)
- [GATT Service Caching](#gatt-service-caching)

---

## Connection Parameter Updates

### When to Request

Request updated connection parameters when your peripheral's role changes:

| Scenario | Priority | Rationale |
|----------|----------|-----------|
| About to start L2CAP transfer | `High` | 11-15ms interval for throughput |
| About to start OTA DFU | `High` | Minimize firmware transfer time |
| Idle monitoring (HRM, glucose) | `Balanced` | 30-50ms interval is sufficient |
| Background logging | `LowPower` | 100-125ms interval saves battery |

### Requesting a Priority Change

```kotlin
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.ExperimentalBleApi

@OptIn(ExperimentalBleApi::class)
suspend fun boostForTransfer(peripheral: Peripheral) {
    val ok = peripheral.requestConnectionPriority(ConnectionPriority.High)
    if (ok) {
        try {
            transferLargePayload(peripheral)
        } finally {
            peripheral.requestConnectionPriority(ConnectionPriority.Balanced)
        }
    }
}
```

### Requesting Specific Parameters

For precise control over interval, latency, and supervision timeout:

```kotlin
import com.atruedev.kmpble.connection.ConnectionParameters
import kotlin.time.Duration.Companion.milliseconds

val params = ConnectionParameters(
    intervalRange = 15.milliseconds..30.milliseconds,
    slaveLatency = 0, // no skipped events = lowest latency
    supervisionTimeout = 2_000.milliseconds,
)
val result = peripheral.requestConnectionParameterUpdate(params)
if (result != null) {
    log("Negotiated: interval=${result.negotiatedInterval}, latency=${result.negotiatedLatency}")
}
```

### Verifying the Update

On Android (API 29+), the `onConnectionUpdated` callback reports actual negotiated values. On iOS, CoreBluetooth does not expose this API; requests return `null`.

```kotlin
// Android only: the negotiated result IS the verification
val result = peripheral.requestConnectionParameterUpdate(params)
// result is non-null = accepted, with OS-negotiated values
```

### Platform Notes

- **Android**: `requestConnectionPriority` maps to `BluetoothGatt.requestConnectionPriority`. Only three levels available; the OS picks the closest.
- **iOS**: No-op. CoreBluetooth manages connection parameters internally.

---

## PHY Selection

### PHY Types

| PHY | Speed | Range | Use Case |
|-----|-------|-------|----------|
| `Le1M` | 1 Mbps | ~30m indoor | Universal fallback |
| `Le2M` | 2 Mbps | ~15m indoor | Firmware transfer, audio streaming |
| `LeCoded` | 125-500 Kbps | ~100m+ outdoor | Long-range sensor networks |

### Setting Preferred PHY

```kotlin
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.ExperimentalBleApi

@OptIn(ExperimentalBleApi::class)
suspend fun enable2Mbps(peripheral: Peripheral) {
    val result = peripheral.setPreferredPhy(Phy.Le2M, Phy.Le2M)
    if (result != null) {
        log("PHY negotiated: TX=${result.txPhy}, RX=${result.rxPhy}")
        if (result.txPhy != Phy.Le2M) {
            log("WARN: 2M not available - controller chose ${result.txPhy}")
        }
    }
}
```

### Monitoring PHY Changes

```kotlin
import com.atruedev.kmpble.connection.PhyUpdate

peripheral.phyUpdate.collect { update: PhyUpdate ->
    log("PHY changed: TX=${update.txPhy}, RX=${update.rxPhy}")
}
```

### PHY + Connection Parameter Workflow

For maximum throughput (firmware updates, L2CAP):

```kotlin
suspend fun maximizeThroughput(peripheral: Peripheral) {
    // 1. Request 2M PHY
    peripheral.setPreferredPhy(Phy.Le2M, Phy.Le2M)
    // 2. Request high priority connection parameters
    peripheral.requestConnectionPriority(ConnectionPriority.High)
    // 3. Request maximum MTU
    peripheral.requestMtu(247)
    // 4. Transfer
    transferFirmware(peripheral)
    // 5. Restore
    peripheral.requestConnectionPriority(ConnectionPriority.Balanced)
}
```

### Platform Notes

- **Android** (API 26+): `setPreferredPhy` + `readPhy` + `phyUpdate` fully supported.
- **iOS**: No public PHY API. `setPreferredPhy` returns `null`, `phyUpdate` never emits.

---

## Beacon Scanning + Connection Coexistence

BLE scanning and maintaining connections are not mutually exclusive. Here's how to run both simultaneously.

### Concurrent Scan and Connection

```kotlin
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun scanAndConnect() = coroutineScope {
    val scanner: Scanner = createScanner {
        timeout = null // unbounded
        emission = EmissionPolicy.FirstThenChanges()
    }

    // Launch scan collection
    val scanJob = launch {
        scanner.scanEvents.collect { event ->
            when (event) {
                is ScanEvent.Found -> {
                    // Connect to matching devices while continuing to scan
                    if (matchesFilter(event.advertisement)) {
                        launch {
                            val peripheral: Peripheral = event.advertisement.toPeripheral()
                            peripheral.connect()
                            handlePeripheral(peripheral)
                        }
                    }
                }
                is ScanEvent.Failed -> log("Scan error: ${event.error}")
            }
        }
    }

    // ... app lifecycle ...
    scanJob.cancel()
    scanner.close()
}
```

### Beacon-Specific Coexistence

Use `BeaconScanner` to filter only beacon advertisements while maintaining BLE connections:

```kotlin
import com.atruedev.kmpble.beacon.BeaconScanner
import com.atruedev.kmpble.beacon.BeaconEvent
import com.atruedev.kmpble.beacon.Beacon

val scanner = createScanner { filters { serviceUuids("feaa") } }
val beaconScanner = BeaconScanner(scanner, scope)

scope.launch {
    beaconScanner.beaconEvents.collect { event ->
        when (event) {
            is BeaconEvent.Found -> {
                val beacon: Beacon = event.beacon
                when (beacon) {
                    is Beacon.IBeacon -> log("iBeacon: ${beacon.uuid} major=${beacon.major}")
                    is Beacon.EddystoneUid -> log("Eddystone-UID: ${beacon.namespace}")
                    is Beacon.EddystoneUrl -> log("Eddystone-URL: ${beacon.url}")
                    is Beacon.EddystoneTlm -> log("Eddystone-TLM: temp=${beacon.temperature}")
                }
            }
            is BeaconEvent.Failed -> log("Beacon error: ${event.error}")
        }
    }
}

beaconScanner.start()
// ... connections can be active during beacon scanning ...
beaconScanner.stop()
beaconScanner.close()
```

### Resource Management

- Android: Scanning and maintaining connections share the Bluetooth radio. Scanning may reduce connection throughput.
- iOS: CoreBluetooth handles coexistence transparently. No action needed.

---

## Periodic Advertising

Periodic advertising (Bluetooth 5.0+) broadcasts data on secondary advertising channels at a fixed interval, enabling connectionless data delivery to multiple scanners.

### Starting Periodic Advertising

```kotlin
import com.atruedev.kmpble.server.ExtendedAdvertiser
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.PeriodicAdvertisingParameters
import com.atruedev.kmpble.connection.Phy
import kotlin.time.Duration.Companion.milliseconds

val advertiser: ExtendedAdvertiser = createExtendedAdvertiser()

val config = ExtendedAdvertiseConfig(
    data = buildSensorPayload(),
    phy = Phy.Le2M,
    periodicAdvertising = PeriodicAdvertisingParameters(
        interval = 100.milliseconds, // broadcast every 100ms
    ),
)

val setId = advertiser.startAdvertisingSet(config)
log("Periodic advertising started: setId=$setId")
```

### Sensor Broadcast Use Case

```kotlin
data class SensorPayload(
    val temperature: Float,
    val humidity: Float,
    val battery: Int,
)

fun buildSensorPayload(): ByteArray {
    val payload = SensorPayload(
        temperature = 23.5f,
        humidity = 54.2f,
        battery = 87,
    )
    return encodeToAdvertisingData(payload) // custom encoding
}

// Multiple scanners receive the broadcast without connecting
// Scanner side:
val scanner = createScanner {
    legacyOnly = false // receive extended advertisements
    filters { serviceUuid("181a") } // Environmental Sensing
}
```

### Platform Notes

- **Android** (API 26+): Full periodic advertising support via `AdvertisingSet`.
- **iOS**: CoreBluetooth does not expose periodic advertising. Falls back to legacy advertising.

---

## Extended Advertising

Extended advertising (Bluetooth 5.0+) supports payloads up to 254 bytes (vs 31 for legacy) and PHY selection.

### When to Use Extended Advertising

| Scenario | Legacy (31 bytes) | Extended (254 bytes) |
|----------|-------------------|---------------------|
| Device name + 1 service UUID | Fits in 31 bytes | Overkill |
| Full scan response with manufacturer data | May not fit | Choose extended |
| Multi-service advertisement | Won't fit | Required |
| PHY selection (2M, Coded) | Not available | Required |

### Starting Extended Advertising

```kotlin
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.connection.Phy

val config = ExtendedAdvertiseConfig(
    data = largeAdvertisingPayload,
    phy = Phy.Le2M, // double throughput on advertising channels
    // periodicAdvertising = null // no periodic broadcast
)

val setId = advertiser.startAdvertisingSet(config)
```

### Multiple Advertising Sets

Android supports multiple concurrent advertising sets (hardware-dependent):

```kotlin
val sensorSetId = advertiser.startAdvertisingSet(sensorConfig)
val beaconSetId = advertiser.startAdvertisingSet(beaconConfig)
// Both advertising simultaneously

// Stop individual sets
advertiser.stopAdvertisingSet(sensorSetId)
// Beacon set continues

// Stop all
advertiser.close()
```

### Scanners Receiving Extended Advertisements

Set `legacyOnly = false` on the scanner to receive extended advertisements:

```kotlin
val scanner = createScanner {
    legacyOnly = false
    phy = ScanPhy.All // scan on all PHYs (1M + Coded)
}
```

---

## Connection Quality Monitoring

### Instantiation and Lifecycle

```kotlin
import com.atruedev.kmpble.monitoring.ConnectionQualityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect

val scope: CoroutineScope = applicationScope
val monitor = ConnectionQualityMonitor(peripheral, scope)

// Start monitoring (idempotent)
monitor.start()

// Observe quality metrics
scope.launch {
    monitor.connectionQuality.collect { quality ->
        updateDashboard(
            connections = quality.totalConnections,
            disconnections = quality.totalDisconnections,
            reconnections = quality.reconnectionCount,
            isConnected = quality.isConnected,
            rssi = quality.lastRssi,
        )
    }
}

// Feed RSSI readings
scope.launch {
    while (isActive) {
        delay(5.seconds)
        val rssi = peripheral.readRssi()
        monitor.recordRssi(rssi)
    }
}

// Stop monitoring
monitor.stop()
```

### ConnectionQuality Fields

| Field | Type | Meaning |
|-------|------|---------|
| `totalConnections` | `Int` | Successful connections established |
| `totalDisconnections` | `Int` | All disconnections (all causes) |
| `reconnectionCount` | `Int` | `max(0, totalConnections - 1)` |
| `lastRssi` | `Int?` | Most recent RSSI reading |
| `isConnected` | `Boolean` | Currently in `State.Connected` |

### Dashboard Integration

```kotlin
data class ConnectionHealthDashboard(
    val deviceName: String,
    val quality: ConnectionQuality,
) {
    val stabilityPercent: Float
        get() = if (quality.totalConnections > 0) {
            (1f - quality.totalDisconnections.toFloat() / quality.totalConnections) * 100
        } else 100f

    val signalStrength: String
        get() = when (quality.lastRssi) {
            null -> "Unknown"
            in -50..0 -> "Excellent"
            in -70..-51 -> "Good"
            in -85..-71 -> "Fair"
            else -> "Poor"
        }
}
```

### Alert Thresholds

```kotlin
suspend fun monitorAlerts(monitor: ConnectionQualityMonitor) {
    monitor.connectionQuality.collect { quality ->
        when {
            // Frequent disconnections
            quality.totalDisconnections > 10 && quality.stabilityPercent < 50 ->
                alert("Unstable connection: ${quality.stabilityPercent}% stability")

            // RSSI too weak
            quality.lastRssi != null && quality.lastRssi < -85 ->
                alert("Weak signal: ${quality.lastRssi} dBm - consider moving closer")

            // Excessive reconnections
            quality.reconnectionCount > 20 ->
                alert("Excessive reconnections: ${quality.reconnectionCount} - check device")
        }
    }
}
```

---

## LE Power Control and Path Loss

### Passive Path Loss Monitoring

```kotlin
import com.atruedev.kmpble.monitoring.PowerMonitor
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

val powerMonitor = PowerMonitor(peripheral, scope, txPower = 4) // calibrated TX power
powerMonitor.start()

scope.launch {
    powerMonitor.pathLoss.collect { reading ->
        if (reading != null) {
            when {
                reading.pathLoss < 30 -> log("Close proximity (${reading.pathLoss} dB)")
                reading.pathLoss in 30..60 -> log("Moderate distance (${reading.pathLoss} dB)")
                reading.pathLoss > 60 -> log("Far / obstructed (${reading.pathLoss} dB)")
            }
        }
    }
}

// Periodically record RSSI
scope.launch {
    while (isActive) {
        val rssi = peripheral.readRssi()
        powerMonitor.recordRssi(rssi)
        delay(2.seconds)
    }
}
```

### Active Power Control

```kotlin
import com.atruedev.kmpble.monitoring.LePowerController
import com.atruedev.kmpble.monitoring.PeerPowerResponse

val powerController = LePowerController(peripheral, scope)
powerController.start()

// Request peer to increase transmit power
val response: PeerPowerResponse = powerController.requestPeerPowerChange(targetDbm = -4)
if (response.accepted) {
    log("Peer accepted power change to ${response.actualDbm} dBm")
} else {
    log("Peer rejected power change: ${response.reason}")
}
```

### Combined Monitoring + Control

```kotlin
// Monitor path loss; request power increase when signal degrades
scope.launch {
    powerMonitor.pathLoss.collect { reading ->
        if (reading != null && reading.pathLoss > 50) {
            val response = powerController.requestPeerPowerChange(-4)
            if (response.accepted) {
                // Give peer time to adjust, then verify
                delay(1.seconds)
                val rssi = peripheral.readRssi()
                powerMonitor.recordRssi(rssi)
            }
        }
    }
}
```

### Platform Notes

- **Android**: `LePowerController` translates target dBm into connection parameter requests. No fine-grained LEPC GATT procedure yet.
- **iOS**: CoreBluetooth does not expose LE Power Control. `requestPeerPowerChange` returns `accepted = false`.

---

## GATT Service Caching

GATT service discovery is expensive (multiple round-trips). Cache discovered services for fast reconnection.

### Basic Caching

```kotlin
import com.atruedev.kmpble.gatt.cache.createGattCache
import com.atruedev.kmpble.peripheral.Peripheral

val cache = createGattCache(maxSize = 32)

suspend fun discoverOrCached(peripheral: Peripheral): List<DiscoveredService> {
    // Check cache first
    cache.get(peripheral.identifier)?.let { return it }

    // Cache miss - discover and store
    val services = peripheral.refreshServices()
    cache.put(peripheral.identifier, services)
    return services
}
```

### Cache Invalidation

Invalidate the cache when:
- The peripheral firmware was updated (GATT database may have changed)
- Service discovery fails with a stale handle error
- The bond was removed (some peripherals show different services after bonding)

```kotlin
// After firmware update
cache.invalidate(peripheral.identifier)

// After bond removal
peripheral.removeBond()
cache.invalidate(peripheral.identifier)

// Clear all caches (e.g., on app reset)
cache.clear()
```

### Platform Notes

- **Android**: In-memory LRU cache. Essential for fast reconnection.
- **iOS**: Passthrough stub. CoreBluetooth caches services automatically.
