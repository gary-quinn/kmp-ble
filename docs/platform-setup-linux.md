# Platform Setup: Linux (JVM + BlueZ)

This guide covers kmp-ble on Linux desktop and edge hosts through the `kmp-ble-bluez` backend, which talks to BlueZ over the system D-Bus. The backend supports the central role (scan, connect, GATT client, bonding) and the peripheral role (GATT server, advertising). See [ADR-0001](adr/ADR-0001-jvm-linux-bluez.md) and [ADR-0003](adr/ADR-0003-jvm-backend-spi.md) for the design.

## Requirements

- Linux with BlueZ 5.x (`bluetoothd` running). BlueZ 5.62+ reports the negotiated MTU; older daemons report 23.
- Java 17+
- A working adapter (for example `hci0` in `bluetoothctl list`)
- Access to `org.bluez` on the **system** D-Bus bus: membership in the `bluetooth` group, an equivalent polkit rule, or a distribution policy that allows every local user (Ubuntu 24.04 does)

## Gradle dependency

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:<version>")
        }
        jvmMain.dependencies {
            implementation("com.atruedev:kmp-ble-bluez:<version>")
        }
    }
}
```

The module registers `BlueZBackend` through `META-INF/services`, so the portable API uses it automatically when the process runs on Linux. Nothing touches D-Bus until you scan, connect, or open a server.

## Activation

| Entry point | Behavior |
|-------------|----------|
| `Scanner { }`, `Advertisement.toPeripheral()`, `BluetoothAdapter()`, `GattServer { }`, `Advertiser()`, `ExtendedAdvertiser()` | Use BlueZ on Linux when `kmp-ble-bluez` is on the classpath |
| `BlueZScanner { }` | Explicit BlueZ scanner, same pipeline as `Scanner { }` |
| `Advertisement.toBlueZPeripheral()` | Explicit BlueZ peripheral for an advertisement from any JVM scanner |
| `FakeScanner { }`, `FakePeripheral { }` | Unit tests and CI without hardware |

Optional system properties:

- `kmpble.bluez.adapter=hci0` selects an adapter by name or MAC (default: first adapter)
- `kmpble.backend=bluez` forces this backend when several are on the classpath

## Quick start

```kotlin
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.scanBatch
import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.time.Duration.Companion.seconds

suspend fun readFirstHeartRate() {
    val ad = Scanner { filters { match { serviceUuid("180d") } } }.use { it.scanBatch(limit = 1, timeout = 10.seconds) }.first()
    val peripheral = ad.toPeripheral()
    peripheral.connect()
    val hr = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!
    peripheral.observeValues(hr).collect { println(it.contentToString()) }
}
```

Or run the bundled CLI:

```bash
./gradlew :sample-jvm:run --args="scan 10"
```

## Feature notes

| Feature | BlueZ behavior |
|---------|----------------|
| Scan | `Adapter1.StartDiscovery` with `Transport=le`, `DuplicateData=true`, and the service UUIDs every filter group requires. Filters, deduplication, and timeouts run in core. `isConnectable` is derived from `Device1.AdvertisingFlags`, an experimental property that `bluetoothd` only publishes with `--experimental`; without it every advertisement reports `isConnectable = false`. |
| Connect / discovery | `Device1.Connect`, then the GATT table from `ObjectManager.GetManagedObjects` once `ServicesResolved` is true. A cancelled or timed-out connect sends `Device1.Disconnect`. |
| MTU | `GattCharacteristic1.MTU` (BlueZ 5.62+). `requestMtu()` returns the negotiated value; BlueZ negotiates on its own. |
| Writes | `WithResponse` maps to `type=request`, `WithoutResponse` and `Signed` to `type=command`, `writeReliable()` to `type=reliable`. |
| Notifications | `StartNotify` / `StopNotify`; values arrive through `PropertiesChanged` on `Value`. A GATT table change after discovery triggers rediscovery. |
| RSSI | `readRssi()` returns the last value BlueZ reported. BlueZ has no D-Bus call for the RSSI of a connected link, so it fails with `GattStatus.RequestNotSupported` when none is cached. |
| Bonding | `bondState` follows `Paired`/`Bonded`; `BondingPreference.Required` calls `Device1.Pair`; `removeBond()` calls `Adapter1.RemoveDevice`. |
| Pairing prompts | Setting `ConnectionOptions.pairingHandler` registers a process-wide `org.bluez.Agent1` (capability `KeyboardDisplay`) and makes it the default agent while any handler is set. Requests for devices without a handler are rejected. |
| GATT server | `GattManager1.RegisterApplication`. BlueZ manages CCCDs and does not name the subscribing central, so `notify()` reaches every subscriber and long (prepared) writes are rejected with `InvalidOffset`. `bluetoothd` passes no write type, so `onWrite` always sees `responseNeeded = true`; on a characteristic that also allows write without response it acknowledges every write before the handler runs and ignores the returned status. Writes reach the handlers in the order `bluetoothd` sends them. |
| Advertising | `LEAdvertisingManager1.RegisterAdvertisement`, one object per set. Interval, TX power, and the secondary PHY are experimental BlueZ properties and only apply when `bluetoothd` runs with `--experimental`. Periodic advertising is not supported. |
| Adapter state | `Adapter1.Powered`, updated live. `getBondedDevices()` lists paired devices. Powering the adapter off during a connection ends it in `Disconnected.BySystemEvent`; other link losses end in `Disconnected.ByError(ConnectionLost)` about 500 ms after `Device1.Connected` turns false, because BlueZ reports the disconnect before the adapter state. |
| Permissions | `checkBlePermissions()` returns `Granted` when `org.bluez` answers on the system bus, `PermanentlyDenied` on D-Bus access denial, and `Denied` when `bluetoothd` is unreachable. |
| Not supported | L2CAP channels and listeners (BlueZ exposes LE CoC only through `AF_BLUETOOTH` sockets), PHY and connection-parameter requests, subrating, isochronous channels, PAST, direction finding. |

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `UnsupportedOperationException` from `Scanner { }` | `kmp-ble-bluez` is not on the runtime classpath, or the process is not on Linux | Add the dependency to `jvmMain`; use `FakeScanner` in tests |
| `ScanEvent.Failed` with `BlueZScanner.ERROR_DBUS_UNAVAILABLE` | `bluetoothd` not running or no D-Bus permission | `sudo systemctl start bluetooth`; add the user to the `bluetooth` group |
| `ScanEvent.Failed` with `ERROR_ADAPTER_OFF` | Adapter powered off | `bluetoothctl power on` |
| `ConnectionFailed` with `platformCode = BlueZ.ERROR_DEVICE_NOT_FOUND` | BlueZ has no object for the address (never discovered or removed) | Scan for the device first |
| Pairing fails immediately | No agent available for a device that needs input | Pass a `pairingHandler` in `ConnectionOptions`, or pair once with `bluetoothctl` |
| Empty scan on hardware | Filter rejected or no LE devices nearby | Enable `BleLogConfig.logger` and look for `SetDiscoveryFilter` warnings |

## CI note

GitHub Actions `jvmTest` does **not** need BlueZ. Backend tests use fake D-Bus sessions (`FakeBlueZAdapterSession`, `FakeBlueZDeviceSession`) and, for the D-Bus call semantics the fakes cannot model, an in-process D-Bus daemon (`DbusBlueZDeviceSessionTest`, `DbusBlueZGattServerTest`). Core conformance tests use `FakeScanner` / `FakePeripheral`. Hardware checks are manual (see `TESTING.md`), which also describes running `bluetoothd` against virtual controllers when no radio is available.

## See also

- [ADR-0001: JVM Linux BlueZ](adr/ADR-0001-jvm-linux-bluez.md)
- [ADR-0003: JVM backend SPI](adr/ADR-0003-jvm-backend-spi.md)
- [Platform Setup: macOS (JVM)](platform-setup-macos.md)
- [Platform Setup: Android](platform-setup-android.md)
- [Platform Setup: iOS](platform-setup-ios.md)
