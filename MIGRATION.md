# Migration Guide: v0.8.x to v0.9.0

This guide covers breaking API changes, new features, and deprecations when
upgrading from kmp-ble v0.8.x to v0.9.0.

## Requirements

- Kotlin 2.3.0+
- Android minSdk 33
- iOS 15+
- kotlinx-coroutines 1.10+

## Breaking Changes

### 1. Typed Error Hierarchy

v0.8.x threw raw exceptions on GATT and connection failures. v0.9.0 introduces a
composable sealed-interface error hierarchy.

**Before (0.8.x):**

```kotlin
try {
    peripheral.read(characteristic)
} catch (e: Exception) {
    when {
        e.message?.contains("GATT") == true -> handleGatt()
        e.message?.contains("connect") == true -> handleConnection()
        else -> handleUnknown()
    }
}
```

**After (0.9.0):**

```kotlin
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.AuthenticationFailed
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.error.MtuExceeded

try {
    peripheral.read(characteristic)
} catch (e: BleException) {
    when (e.error) {
        is GattError -> handleGattError(e.error) // includes recoveryHint
        is ConnectionLost -> handleDisconnect()
        is AuthenticationFailed -> promptPairing()
        is StaleGattHandle -> reconnect()
        is MtuExceeded -> {
            val m = e.error as MtuExceeded
            peripheral.requestMtu(m.attempted)
        }
        else -> logError(e.error)
    }
}
```

### 2. ConnectionFailed and ConnectionLost Include recoveryHint

Connection errors now carry a `recoveryHint` property with actionable guidance.
Update any code that inspects these types.

**Before (0.8.x):**

```kotlin
catch (e: ConnectionFailed) {
    showError("Connection failed: ${e.reason}")
}
```

**After (0.9.0):**

```kotlin
import com.atruedev.kmpble.error.ConnectionFailed

catch (e: ConnectionFailed) {
    showError(e.recoveryHint) // "Check Bluetooth is enabled and the peripheral is in range."
}
```

### 3. readPhy() and setPreferredPhy() Added to Peripheral

The `Peripheral` interface now includes PHY control methods. Compile errors on
classes implementing `Peripheral` directly: add the new methods (or extend
`PeripheralSupport` for delegate-based implementations).

**Before (0.8.x):**

```kotlin
// No PHY control API available
// Manually estimate connection throughput
```

**After (0.9.0):**

```kotlin
import com.atruedev.kmpble.connection.Phy

val result = peripheral.readPhy()
if (result != null) {
    println("TX=${result.tx}, RX=${result.rx}")
}

peripheral.setPreferredPhy(Phy.Le2M, Phy.Le2M)

peripheral.phyUpdate.collect { update ->
    println("PHY changed: TX=${update.txPhy}, RX=${update.rxPhy}")
}
```

### 4. requestConnectionParameterUpdate() Added

The new fine-grained connection parameter API replaces the coarse
`requestConnectionPriority()` for callers that need specific interval/latency
control. `requestConnectionPriority()` is **not deprecated** -- it remains the
simpler option for High/Balanced/PowerSave use cases.

```kotlin
import com.atruedev.kmpble.connection.ConnectionParameters
import kotlin.time.Duration.Companion.milliseconds

val result = peripheral.requestConnectionParameterUpdate(
    ConnectionParameters(
        intervalRange = 11.25.milliseconds..15.milliseconds,
        slaveLatency = 0,
        supervisionTimeout = 500.milliseconds,
    )
)
if (result != null) {
    println("Negotiated interval: ${result.negotiatedInterval}")
}
```

### 5. ExtendedAdvertiser periodicAdvertising Parameter

`ExtendedAdvertiseConfig` has a new `periodicAdvertising` parameter. Callers
constructing `ExtendedAdvertiseConfig` with positional arguments need to add
the parameter (all parameters have defaults, so named-argument callers are
unaffected).

**Before (0.8.x):**

```kotlin
val config = ExtendedAdvertiseConfig(
    serviceUuids = listOf(uuidFrom("180d")),
    primaryPhy = Phy.Le1M,
)
extAdvertiser.startAdvertisingSet(config)
```

**After (0.9.0):**

```kotlin
import com.atruedev.kmpble.server.PeriodicAdvertisingParameters
import com.atruedev.kmpble.server.AdvertiseInterval

val config = ExtendedAdvertiseConfig(
    serviceUuids = listOf(uuidFrom("180d")),
    primaryPhy = Phy.Le1M,
    periodicAdvertising = PeriodicAdvertisingParameters(
        includeTxPower = true,
        interval = AdvertiseInterval.Balanced,
    ),
)
extAdvertiser.startAdvertisingSet(config)
```

### 6. GattCache Changes (synchronized to Mutex)

AndroidGattCache no longer uses `synchronized`. If your code held a reference to
the cache internals, the thread-safety mechanism has changed. Public API is
unchanged.

### 7. ConnectionOptions.timeout Replaced by timeouts: OperationTimeouts

The single `timeout` parameter has been replaced by per-operation
`OperationTimeouts` for fine-grained control over connect, service discovery,
read, write, MTU, and L2CAP operation deadlines.

A deprecated secondary constructor accepts the legacy `timeout` parameter and
maps it to `timeouts.connect`, so existing call sites still compile with a
warning. Migrate at your own pace.

**Before (0.8.x):**

```kotlin
val options = ConnectionOptions(
    timeout = 30.seconds,
    mtuRequest = 512,
)
```

**After (0.9.0):**

```kotlin
import com.atruedev.kmpble.connection.OperationTimeouts

val options = ConnectionOptions(
    timeouts = OperationTimeouts(
        connect = 30.seconds,
        serviceDiscovery = 15.seconds,
        read = 5.seconds,
        write = 5.seconds,
    ),
    mtuRequest = 512,
)
```

All `OperationTimeouts` fields have sensible defaults (30s connect, 15s
discovery, 5s read/write, 10s MTU/L2CAP). Omit any you do not need to
customize.

## New Features

### PHY Selection for Scanning

`ScannerConfig` now supports PHY selection via `ScanPhy`, including LE Coded for
long-range scanning.

```kotlin
import com.atruedev.kmpble.scanner.ScanPhy

val scanner = Scanner {
    phy = ScanPhy.LeCoded // Long-range (BLE 5.0+)
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
    filters { match { serviceUuid(ServiceUuid.HEART_RATE) } }
}
```

See [API Quick Reference](docs/api-quick-reference.md) for more scan patterns.

### Beacon Scanning (iBeacon, Eddystone)

```kotlin
import com.atruedev.kmpble.beacon.BeaconScanner
import com.atruedev.kmpble.beacon.BeaconEvent

val scanner = Scanner { filters { match { serviceUuid("feaa") } } }
val beaconScanner = BeaconScanner(scanner, scope)

scope.launch {
    beaconScanner.beaconEvents.collect { event ->
        when (event) {
            is BeaconEvent.Found -> println("Beacon: ${event.beacon}")
            is BeaconEvent.Failed -> handleError(event.error)
        }
    }
}
beaconScanner.start()
// ... later
beaconScanner.stop()
beaconScanner.close()
```

### L2CAP Listener (Server-Side)

Server-side L2CAP is now available via `L2capListener`, independent of
`GattServer`:

```kotlin
import com.atruedev.kmpble.l2cap.L2capListener
import com.atruedev.kmpble.l2cap.l2capListener

val listener = l2capListener()
listener.open(secure = true)

val psm = listener.psm
// Expose PSM to centrals via GATT characteristic or advertisement

listener.incoming.collect { channel ->
    scope.launch {
        channel.incoming.collect { data -> process(data) }
    }
}
```

### ConnectionQualityMonitor

Stand-alone class that tracks connection/disconnection statistics:

```kotlin
import com.atruedev.kmpble.monitoring.ConnectionQualityMonitor

val monitor = ConnectionQualityMonitor(peripheral, scope)
monitor.start()

monitor.connectionQuality.collect { quality ->
    println("Connected: ${quality.isConnected}")
    println("Total connections: ${quality.totalConnections}")
    println("Total disconnections: ${quality.totalDisconnections}")
}

// Feed RSSI readings
val rssi = peripheral.readRssi()
monitor.recordRssi(rssi)

monitor.stop()
```

### LE Power Control and Path Loss Monitoring

```kotlin
import com.atruedev.kmpble.monitoring.PowerMonitor
import com.atruedev.kmpble.monitoring.LePowerController

val powerMonitor = PowerMonitor(peripheral, scope)
val powerController = LePowerController(peripheral, scope)

powerMonitor.start()
powerController.start()

// Passive path loss monitoring
powerMonitor.pathLoss.collect { reading ->
    if (reading != null && reading.pathLoss > 50) {
        // Active power adjustment
        val response = powerController.requestPeerPowerChange(-4)
        if (response.accepted) {
            println("Peer power adjusted to ${response.targetDbm} dBm")
        }
    }
}
```

### L2CAP Framed Writes with Codec

L2CAP channels now support framed writes with `BleCodec` for typed streaming:

```kotlin
import com.atruedev.kmpble.l2cap.writeFramed
import com.atruedev.kmpble.l2cap.framedIncoming

val channel = peripheral.openL2capChannel(psm = 0x25)
channel.writeFramed(myPacket, myCodec)

channel.framedIncoming(myCodec).collect { packet ->
    processTypedPacket(packet)
}
```

### ConnectionRecipe Presets

Pre-built connection option presets for common use cases:

```kotlin
import com.atruedev.kmpble.connection.ConnectionRecipe

peripheral.connect(ConnectionRecipe.MEDICAL)  // strict bonding, no auto-connect
peripheral.connect(ConnectionRecipe.FITNESS)  // reconnection, if-required bonding
peripheral.connect(ConnectionRecipe.IOT)      // auto-connect, no bonding
peripheral.connect(ConnectionRecipe.CONSUMER) // balanced defaults
```

### GATT Service Caching

Fast reconnection by caching discovered GATT services:

```kotlin
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.GattCacheMode

peripheral.connect(ConnectionOptions(
    gattCacheMode = GattCacheMode.Enabled,
))
// On reconnect, services are restored from cache instead of re-discovered
```

## Module Changes

### kmp-ble-benchmark (New)

Benchmark module extracted from the core library. Add as a separate dependency:

```kotlin
// build.gradle.kts
implementation("com.atruedev:kmp-ble-benchmark:0.9.0")
```

### kmp-ble-core (Renamed from kmp-ble)

The core artifact remains `com.atruedev:kmp-ble`. No Gradle changes needed.

## Documentation

- [API Quick Reference](docs/api-quick-reference.md): copy-paste-ready snippets for all workflows
- [Platform Setup: iOS](docs/platform-setup-ios.md): Info.plist keys, background modes
- [Platform Setup: Android](docs/platform-setup-android.md): manifest permissions, runtime flow
- [Troubleshooting](docs/troubleshooting.md): common BLE errors and fixes
- [Architecture](ARCHITECTURE.md): state machine, concurrency, GATT queue design
- [L2CAP Architecture](docs/L2CAP.md): Connection-Oriented Channel subsystem

## Need Help?

If you encounter issues not covered here, open an issue at
[github.com/gary-quinn/kmp-ble](https://github.com/gary-quinn/kmp-ble/issues).
