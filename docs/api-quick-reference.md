# API Quick Reference

Copy-paste-ready snippets for common BLE workflows with kmp-ble. Every example is self-contained with imports and error handling.

## Table of Contents

- [Setup](#setup)
- [Scan and Connect](#scan-and-connect)
- [Discover Services and Characteristics](#discover-services-and-characteristics)
- [Read and Write Characteristics](#read-and-write-characteristics)
- [Subscribe to Notifications and Indications](#subscribe-to-notifications-and-indications)
- [GATT Server and Advertising](#gatt-server-and-advertising)
- [L2CAP Channels](#l2cap-channels)
- [DFU Firmware Updates](#dfu-firmware-updates)
- [Connection Monitoring](#connection-monitoring)
- [Beacon Scanning](#beacon-scanning)
- [LE Power Control](#le-power-control)
- [Error Handling](#error-handling)
- [Testing Without Hardware](#testing-without-hardware)
- [Permissions](#permissions)
- [Logging](#logging)

## Setup

### Android (Application initialization)

```kotlin
import com.atruedev.kmpble.KmpBle

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpBle.init(this)
    }
}
```

### KMP Shared Module (Gradle)

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:0.8.5")
        }
    }
}
```

---

## Scan and Connect

### Basic scan with filters

```kotlin
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ServiceUuid
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

val scanner = Scanner {
    timeout = 30.seconds
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
    filters {
        match { serviceUuid(ServiceUuid.HEART_RATE) }
    }
}

scanner.scanEvents.collect { event ->
    when (event) {
        is ScanEvent.Found -> {
            val ad = event.advertisement
            println("Found: ${ad.name ?: "unnamed"} (${ad.identifier}) rssi=${ad.rssi}")
            // Connect to the first device found
            val peripheral = ad.toPeripheral()
            try {
                peripheral.connect()
                println("Connected to ${ad.name}")
            } catch (e: BleException) {
                println("Connection failed: ${e.error}")
            }
        }
        is ScanEvent.Failed -> println("Scan error: ${event.error.message}")
    }
}

scanner.close()
```

### Scan with multiple filter groups (OR-of-ANDs)

```kotlin
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ServiceUuid

val scanner = Scanner {
    filters {
        // Match heart rate monitors
        match { serviceUuid(ServiceUuid.HEART_RATE) }
        // OR match battery service devices with strong signal
        match {
            serviceUuid("180f")
            rssi(minRssi = -60)
        }
    }
}
```

### Connection recipes

Pre-configured connection options for common device categories:

```kotlin
import com.atruedev.kmpble.connection.ConnectionRecipe

peripheral.connect(ConnectionRecipe.MEDICAL)  // 60s timeout, 10 retry attempts
peripheral.connect(ConnectionRecipe.FITNESS)  // 30s timeout, 5 retry attempts
peripheral.connect(ConnectionRecipe.IOT)      // 15s timeout, 3 retry attempts
peripheral.connect(ConnectionRecipe.CONSUMER) // 20s timeout, 3 retry attempts
```

### Custom connection with reconnection

```kotlin
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.error.BleException
import kotlin.time.Duration.Companion.seconds

try {
    peripheral.connect(ConnectionOptions(
        timeout = 45.seconds,
        bondingPreference = BondingPreference.Required,
        mtuRequest = 247,
        reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            maxAttempts = 10,
        ),
    ))
    println("Connected successfully")
} catch (e: BleException) {
    println("Connection failed: ${e.error}")
}
```

### Monitor adapter state

```kotlin
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState

val adapter = BluetoothAdapter()

adapter.state.collect { state ->
    when (state) {
        BluetoothAdapterState.On -> println("Bluetooth ready")
        BluetoothAdapterState.Off -> println("Bluetooth off")
        BluetoothAdapterState.Unauthorized -> println("Permission denied")
        else -> {}
    }
}
```

### Monitor peripheral connection state

```kotlin
import com.atruedev.kmpble.connection.State

peripheral.state.collect { state ->
    when (state) {
        is State.Connecting -> println("Connecting...")
        is State.Connected.Ready -> println("Connected and ready")
        is State.Disconnecting -> println("Disconnecting...")
        is State.Disconnected.ByRequest -> println("Disconnected by request")
        is State.Disconnected.ByRemote -> println("Disconnected by remote")
        is State.Disconnected.ByError -> println("Error: ${state.error}")
        else -> {}
    }
}
```

---

## Discover Services and Characteristics

### Discover all services

```kotlin
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService

// Services are populated automatically after connect()
val services = peripheral.services.value ?: emptyList()
for (service in services) {
    println("Service: ${service.uuid}")
    for (characteristic in service.characteristics) {
        println("  Characteristic: ${characteristic.uuid}")
        println("    Properties: ${characteristic.properties}")
    }
}
```

### Find a specific characteristic

```kotlin
import com.atruedev.kmpble.scanner.uuidFrom

val hrCharacteristic = peripheral.findCharacteristic(
    serviceUuid = uuidFrom("180d"),    // Heart Rate service
    characteristicUuid = uuidFrom("2a37"),  // Heart Rate Measurement
)

if (hrCharacteristic == null) {
    println("Characteristic not found - device may not support this service")
    peripheral.disconnect()
    return
}
```

### Refresh services (force re-discovery)

```kotlin
try {
    val refreshedServices = peripheral.refreshServices()
    println("Discovered ${refreshedServices.size} services")
} catch (e: BleException) {
    println("Service refresh failed: ${e.error}")
}
```

---

## Read and Write Characteristics

### Read a characteristic

```kotlin
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError

try {
    val value: ByteArray = peripheral.read(characteristic)
    println("Read ${value.size} bytes: ${value.joinToString { "%02X".format(it) }}")
} catch (e: BleException) {
    when (val error = e.error) {
        is GattError -> println("GATT error (${error.status}): ${error.recoveryHint}")
        else -> println("Read failed: ${error.recoveryHint}")
    }
}
```

### Write with response

```kotlin
import com.atruedev.kmpble.gatt.WriteType

try {
    peripheral.write(
        characteristic = characteristic,
        data = byteArrayOf(0x01, 0x00),  // Enable notifications
        writeType = WriteType.WithResponse,
    )
    println("Write acknowledged")
} catch (e: BleException) {
    println("Write failed: ${e.error.recoveryHint}")
}
```

### Write without response

```kotlin
peripheral.write(
    characteristic = characteristic,
    data = byteArrayOf(0x55, 0xAA),
    writeType = WriteType.WithoutResponse,
)
// No confirmation from the peripheral
```

### Request MTU

```kotlin
val negotiatedMtu = peripheral.requestMtu(512)
println("Negotiated MTU: $negotiatedMtu")
```

### Read RSSI

```kotlin
val rssi = peripheral.readRssi()
println("Current RSSI: $rssi dBm")
```

### Read and write descriptors

```kotlin
import com.atruedev.kmpble.scanner.uuidFrom

val cccdDescriptor = peripheral.findDescriptor(
    serviceUuid = uuidFrom("180d"),
    characteristicUuid = uuidFrom("2a37"),
    descriptorUuid = uuidFrom("2902"),  // CCCD
)

if (cccdDescriptor != null) {
    // Read current CCCD value
    val cccdValue = peripheral.readDescriptor(cccdDescriptor)
    println("CCCD: ${cccdValue.joinToString { "%02X".format(it) }}")

    // Enable notifications (0x0001)
    peripheral.writeDescriptor(cccdDescriptor, byteArrayOf(0x01, 0x00))
}
```

### Connection Priority

```kotlin
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.ExperimentalBleApi

@OptIn(ExperimentalBleApi::class)
suspend fun optimizeForThroughput() {
    // High priority for bulk operations
    peripheral.requestConnectionPriority(ConnectionPriority.High)
    // ... perform high-throughput operations ...
    // Reset to balanced when done
    peripheral.requestConnectionPriority(ConnectionPriority.Balanced)
}
```

### PHY selection (BLE 5.0)

```kotlin
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.ExperimentalBleApi

@OptIn(ExperimentalBleApi::class)
suspend fun optimizePhy() {
    // Request 2M PHY for double throughput
    val result = peripheral.setPreferredPhy(tx = Phy.Le2M, rx = Phy.Le2M)
    if (result != null) {
        println("PHY negotiated: tx=${result.txPhy}, rx=${result.rxPhy}")
    }
}
```

---

## Subscribe to Notifications and Indications

### Observe raw values (simplest)

```kotlin
import com.atruedev.kmpble.gatt.BackpressureStrategy

// observeValues suspends during disconnects, resumes on reconnect - no error handling needed
peripheral.observeValues(characteristic, BackpressureStrategy.Unbounded)
    .collect { data: ByteArray ->
        // Parse your data here
        println("Received ${data.size} bytes")
    }
```

### Observe with connection lifecycle visibility

```kotlin
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.BackpressureStrategy

peripheral.observe(characteristic, BackpressureStrategy.Latest)
    .collect { event ->
        when (event) {
            is Observation.Value -> {
                val heartRate = event.data[1].toInt() and 0xFF
                println("Heart rate: $heartRate BPM")
            }
            is Observation.Disconnected -> {
                println("Device disconnected - waiting for reconnect...")
            }
        }
    }
```

### Heart rate monitor example (end-to-end)

```kotlin
import com.atruedev.kmpble.scanner.*
import com.atruedev.kmpble.gatt.*
import com.atruedev.kmpble.error.*
import kotlinx.coroutines.flow.first

suspend fun monitorHeartRate() {
    val scanner = Scanner {
        timeout = 15.seconds
        filters { match { serviceUuid(ServiceUuid.HEART_RATE) } }
    }

    val found = scanner.scanEvents
        .mapNotNull { (it as? ScanEvent.Found)?.advertisement }
        .first()

    val peripheral = found.toPeripheral()
    peripheral.connect()

    val hrChar = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!

    peripheral.observeValues(hrChar).collect { data ->
        val flags = data[0].toInt() and 0xFF
        val hrFormat = (flags and 0x01)  // 0 = UINT8, 1 = UINT16
        val heartRate = if (hrFormat == 1) {
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        } else {
            data[1].toInt() and 0xFF
        }
        println("Heart rate: $heartRate BPM")
    }

    peripheral.disconnect()
    scanner.close()
}
```

---

## GATT Server and Advertising

### GATT server with custom service

```kotlin
import com.atruedev.kmpble.server.*
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.scanner.uuidFrom

val server = GattServer {
    service(uuidFrom("180d")) {
        characteristic(uuidFrom("2a37")) {
            properties { read = true; notify = true }
            permissions { read = true }
            onRead { device ->
                // Return current value when read
                BleData(byteArrayOf(0x00, 72))
            }
        }
        characteristic(uuidFrom("2a38")) {
            properties { write = true; writeWithoutResponse = true }
            permissions { write = true }
            onWrite { device, data, responseNeeded ->
                handleCommand(data.toByteArray())
                if (responseNeeded) GattStatus.Success else null
            }
        }
    }
}

server.open()
println("GATT server open")

// Notify connected clients when data changes
server.notify(
    characteristicUuid = uuidFrom("2a37"),
    device = null,  // null = broadcast to all subscribed
    data = BleData(byteArrayOf(0x00, 80)),
)

// Clean up
server.close()
```

### Legacy advertising

```kotlin
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseMode

val advertiser = Advertiser()

advertiser.startAdvertising(AdvertiseConfig(
    name = "My BLE Device",
    serviceUuids = listOf(uuidFrom("180d")),
    connectable = true,
    mode = AdvertiseMode.Balanced,
))

// Monitor advertising state
advertiser.isAdvertising.collect { active ->
    println("Advertising: $active")
}

// Later...
advertiser.stopAdvertising()
advertiser.close()
```

### Extended advertising (BLE 5.0)

```kotlin
import com.atruedev.kmpble.server.ExtendedAdvertiser
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseInterval
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.ExperimentalBleApi

@OptIn(ExperimentalBleApi::class)
suspend fun startExtendedAdvertising() {
    val advertiser = ExtendedAdvertiser()

    val setId = advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(
        name = "MyDevice",
        serviceUuids = listOf(uuidFrom("180d")),
        primaryPhy = Phy.Le1M,
        secondaryPhy = Phy.Le2M,
        interval = AdvertiseInterval.LowLatency,
        connectable = true,
    ))

    println("Advertising set $setId active")

    advertiser.stopAdvertisingSet(setId)
    advertiser.close()
}
```

### Handle server connections

```kotlin
server.connectionEvents.collect { event ->
    when (event) {
        is ServerConnectionEvent.Connected -> println("${event.device} connected")
        is ServerConnectionEvent.Disconnected -> println("${event.device} disconnected")
    }
}

// Current connections snapshot
val connectedClients = server.connections.value
```

---

## L2CAP Channels

### Client: open L2CAP channel to peripheral

```kotlin
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException

try {
    val channel: L2capChannel = peripheral.openL2capChannel(
        psm = 0x25,
        secure = true,
    )

    println("Channel open, PSM=${channel.psm}, MTU=${channel.mtu}")

    // Write data
    channel.write(byteArrayOf(0x01, 0x02, 0x03))

    // Read incoming data
    channel.incoming.collect { data: ByteArray ->
        println("Received ${data.size} bytes via L2CAP")
        processL2capData(data)
    }

    channel.close()
} catch (e: L2capException.NotConnected) {
    println("Peripheral not connected")
} catch (e: L2capException.OpenFailed) {
    println("L2CAP channel open failed")
} catch (e: L2capException.NotSupported) {
    println("L2CAP not available on this platform")
}
```

### Server: accept L2CAP connections

```kotlin
import com.atruedev.kmpble.l2cap.L2capListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val listener = L2capListener()

listener.open(secure = true)
println("L2CAP listener open on PSM ${listener.psm}")

// Accept incoming channels
scope.launch {
    listener.incoming.collect { channel ->
        println("Accepted L2CAP channel: PSM=${channel.psm}, MTU=${channel.mtu}")

        // Handle each channel in its own coroutine
        launch {
            try {
                channel.incoming.collect { data ->
                    println("L2CAP received: ${data.size} bytes")
                    channel.write(byteArrayOf(0x06))  // Echo response
                }
            } finally {
                channel.close()
            }
        }
    }
}

// Clean up
listener.close()
```

---

## DFU Firmware Updates

See [kmp-ble-dfu](https://github.com/gary-quinn/kmp-ble) module for full documentation.

### Quick start

```kotlin
import com.atruedev.kmpble.dfu.DfuController
import com.atruedev.kmpble.dfu.DfuProgress

val controller = DfuController.create(peripheral)  // Auto-detect protocol
// Or specify: DfuController(peripheral, NordicDfuProtocol())

controller.performDfu(firmwareBytes).collect { progress ->
    when (progress) {
        is DfuProgress.Transferring -> println("${(progress.fraction * 100).toInt()}%")
        is DfuProgress.Completed -> println("Update complete")
        is DfuProgress.Failed -> println("Failed: ${progress.error}")
        else -> {}
    }
}

controller.abort()  // Cancel mid-transfer
```

### Firmware package parsing

```kotlin
import com.atruedev.kmpble.dfu.FirmwarePackage

val nordicPackage = FirmwarePackage.Nordic.fromZipBytes(zipData)
val mcubootPackage = FirmwarePackage.McuBoot.fromBinBytes(binData)
val espPackage = FirmwarePackage.EspOta.fromBinBytes(binData)
```

---

## Connection Monitoring

### ConnectionQualityMonitor

```kotlin
import com.atruedev.kmpble.monitoring.ConnectionQualityMonitor
import com.atruedev.kmpble.monitoring.ConnectionQuality

val monitor = ConnectionQualityMonitor(peripheral, scope)
monitor.start()

monitor.connectionQuality.collect { quality: ConnectionQuality ->
    println("Connected: ${quality.isConnected}")
    println("Total connections: ${quality.totalConnections}")
    println("Total disconnections: ${quality.totalDisconnections}")
    println("Last RSSI: ${quality.lastRssi}")
}

// Periodically update RSSI
peripheral.readRssi().let { rssi -> monitor.recordRssi(rssi) }

monitor.stop()
```

---

## Beacon Scanning

### iBeacon and Eddystone scanning

```kotlin
import com.atruedev.kmpble.beacon.BeaconScanner
import com.atruedev.kmpble.beacon.BeaconEvent
import com.atruedev.kmpble.beacon.Beacon
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val scanner = Scanner {
    filters { match { serviceUuid("feaa") } }  // Eddystone
}

val beaconScanner = BeaconScanner(scanner, scope)

scope.launch {
    beaconScanner.beaconEvents.collect { event ->
        when (event) {
            is BeaconEvent.Found -> {
                val beacon = event.beacon
                when (beacon) {
                    is Beacon.IBeacon -> println(
                        "iBeacon: uuid=${beacon.uuid} major=${beacon.major} minor=${beacon.minor}"
                    )
                    is Beacon.Eddystone -> println(
                        "Eddystone: ${beacon.namespace}/${beacon.instance}"
                    )
                    is Beacon.AltBeacon -> println("AltBeacon: id1=${beacon.id1}")
                }
            }
            is BeaconEvent.Failed -> println("Scan error: ${event.error}")
        }
    }
}

beaconScanner.start()
// ... scanning ...
beaconScanner.stop()
beaconScanner.close()
```

---

## LE Power Control

### Request peer power change

```kotlin
import com.atruedev.kmpble.monitoring.LePowerController
import com.atruedev.kmpble.ExperimentalBleApi

@OptIn(ExperimentalBleApi::class)
suspend fun adjustPeerPower() {
    val controller = LePowerController(peripheral, scope)
    controller.start()

    // Listen for incoming power requests from the peer
    controller.incomingPowerRequests.collect { request ->
        println("Peer requests power change to ${request.targetDbm} dBm")
    }

    // Request peer to increase transmit power
    val response = controller.requestPeerPowerChange(targetDbm = -4)
    if (response.accepted) {
        println("Peer accepted power change to ${response.targetDbm} dBm")
        println("Negotiated interval: ${response.negotiatedInterval}")
    }

    controller.stop()
}
```

### Passive power monitoring

```kotlin
import com.atruedev.kmpble.monitoring.PowerMonitor
import com.atruedev.kmpble.monitoring.PathLossReading

val powerMonitor = PowerMonitor(peripheral, scope)
powerMonitor.start()

powerMonitor.pathLoss.collect { reading: PathLossReading? ->
    if (reading != null) {
        println("Path loss: ${reading.pathLoss} dB")
        if (reading.pathLoss > 50) {
            println("High path loss - move closer or increase power")
        }
    }
}

powerMonitor.stop()
```

---

## Error Handling

### Structured error handling

All BLE operations throw `BleException` containing a typed `BleError`:

```kotlin
import com.atruedev.kmpble.error.*

try {
    peripheral.read(characteristic)
} catch (e: BleException) {
    when (val error = e.error) {
        is GattError -> println("${error.operation} failed: ${error.status}")
        is ConnectionLost -> println("Connection lost: ${error.reason}")
        is AuthenticationFailed -> println("Auth failed: ${error.recoveryHint}")
        is EncryptionFailed -> println("Encryption failed: ${error.recoveryHint}")
        is MtuExceeded -> println("MTU exceeded: tried ${error.attempted}, max ${error.maximum}")
        is StaleGattHandle -> println("Stale handle: ${error.uuid}")
        is OperationFailed -> println("Operation failed: ${error.message}")
        else -> println("Unknown error: ${error.recoveryHint}")
    }
}
```

### Connection errors

```kotlin
try {
    peripheral.connect()
} catch (e: BleException) {
    when (val error = e.error) {
        is ConnectionFailed -> println("Connect failed: ${error.reason}")
        is AuthenticationFailed -> println(
            "Bonding failed. Try forgetting and re-pairing in system settings."
        )
        else -> println("Connection error: ${error.recoveryHint}")
    }
}
```

### GATT operation errors with retry

```kotlin
import kotlinx.coroutines.delay

suspend fun readWithRetry(
    characteristic: Characteristic,
    maxRetries: Int = 3,
): ByteArray {
    repeat(maxRetries) { attempt ->
        try {
            return peripheral.read(characteristic)
        } catch (e: BleException) {
            when (e.error) {
                is GattError -> {
                    if (attempt < maxRetries - 1) {
                        delay(500)
                    } else throw e
                }
                else -> throw e
            }
        }
    }
    throw IllegalStateException("Unreachable")
}
```

### Disconnect and close safely

```kotlin
try {
    peripheral.disconnect()
} catch (e: CancellationException) {
    throw e  // Always rethrow cancellation
} catch (e: Exception) {
    println("Disconnect error: ${e.message}")
} finally {
    // Always close to release resources
    try {
        peripheral.close()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Best-effort close
    }
}
```

---

## Testing Without Hardware

### FakeScanner and FakePeripheral

```kotlin
import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.Advertisement
import kotlinx.coroutines.test.runTest

@Test
fun `test heart rate observation flow`() = runTest {
    // Create a fake scanner that emits a single advertisement
    val scanner = FakeScanner {
        advertisement {
            name("TestHR")
            rssi(-55)
            serviceUuids("180d")
        }
    }

    // Collect scan events
    val found = scanner.scanEvents
        .mapNotNull { it as? ScanEvent.Found }
        .first()

    val ad: Advertisement = found.advertisement
    assertEquals("TestHR", ad.name)
    assertEquals(-55, ad.rssi)

    // Create a fake peripheral with custom behavior
    val peripheral = FakePeripheralBuilder()
        .apply {
            service("180d") {
                characteristic("2a37") {
                    properties(notify = true, read = true, write = true)
                    onRead { byteArrayOf(0x00, 72) }
                    onObserve {
                        flow {
                            emit(byteArrayOf(0x00, 72))
                            delay(1000)
                            emit(byteArrayOf(0x00, 80))
                        }
                    }
                }
            }
        }
        .build()

    // Exercise the fake peripheral
    peripheral.connect()
    assertEquals("TestHR", peripheral.identifier.value)

    val char = peripheral.findCharacteristic(
        serviceUuid = uuidFrom("180d"),
        characteristicUuid = uuidFrom("2a37"),
    )!!

    val result = peripheral.read(char)
    assertContentEquals(byteArrayOf(0x00, 72), result)

    peripheral.disconnect()
    peripheral.close()
    scanner.close()
}
```

---

## Permissions

```kotlin
import com.atruedev.kmpble.permissions.checkBlePermissions
import com.atruedev.kmpble.permissions.PermissionResult

when (val result = checkBlePermissions()) {
    is PermissionResult.Granted -> {
        // Ready to scan and connect
        startBleOperations()
    }
    is PermissionResult.Denied -> {
        // Show rationale and request permissions
        requestBluetoothPermissions()
    }
    is PermissionResult.PermanentlyDenied -> {
        // Direct user to system settings
        openAppSettings()
    }
}
```

---

## Logging

```kotlin
import com.atruedev.kmpble.logging.BleLogConfig
import com.atruedev.kmpble.logging.PrintBleLogger
import com.atruedev.kmpble.logging.BleLogger

// Simple stdout/logcat output
BleLogConfig.logger = PrintBleLogger()

// Custom logger integration
BleLogConfig.logger = BleLogger { event ->
    // Forward to your logging framework
    Timber.d("BLE: ${event.operation} ${event.result}")
}

// Disable logging
BleLogConfig.logger = BleLogger { /* no-op */ }
```

---

## Related Documentation

- [README](../README.md) - overview and setup
- [ARCHITECTURE.md](../ARCHITECTURE.md) - state machine, concurrency, design
- [L2CAP Architecture](L2CAP.md) - L2CAP subsystem details
- [Platform Setup: iOS](platform-setup-ios.md)
- [Platform Setup: Android](platform-setup-android.md)
- [Troubleshooting](troubleshooting.md)
- [API Reference](https://gary-quinn.github.io/kmp-ble/) - KDoc-generated API docs
