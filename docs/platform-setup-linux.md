# Platform Setup: Linux (JVM + BlueZ)

This guide covers running kmp-ble **LE scan** on a Linux desktop or edge host through
BlueZ and D-Bus. GATT connect, peripheral, and server APIs remain unsupported on JVM
(M1 scope).

## Requirements

- Linux with BlueZ 5.x (`bluetoothd` running)
- Java 17+ for the JVM target
- A working adapter (for example `hci0` visible in `bluetoothctl list`)
- Permission to use the **system** D-Bus bus (often membership in the `bluetooth` group)

## Gradle dependency

Same artifact as other platforms:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:<version>")
        }
    }
}
```

BlueZ D-Bus dependencies are pulled automatically for the JVM source set.

## Activation

| Entry point | When to use |
|-------------|-------------|
| `BlueZScanner { }` | **Recommended** on Linux with BlueZ |
| `Scanner { }` | Throws on CI/headless JVM by default |
| `Scanner { }` + `-Dkmpble.bluez.enabled=true` | Opt-in alias; constructs `BlueZScanner` without probing D-Bus first (failures at collect time) |
| `FakeScanner { }` | Unit tests and CI (no hardware) |

Optional system properties:

- `kmpble.bluez.enabled=true` - allow portable `Scanner { }` to delegate to `BlueZScanner` (lazy; no adapter probe at construction)
- `kmpble.bluez.adapter=hci0` - choose adapter by name or MAC (default: first adapter)

## Quick scan sample

Collect advertisements for ten seconds on a machine where `bluetoothctl` already works:

```kotlin
import com.atruedev.kmpble.scanner.BlueZScanner
import com.atruedev.kmpble.scanner.ScanEvent
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

fun main() = runBlocking {
    BlueZScanner {
        // optional: filters { match { namePrefix("My") } }
    }.use { scanner ->
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        scanner.scanEvents
            .takeWhile { TimeSource.Monotonic.markNow() < deadline }
            .collect { event ->
                when (event) {
                    is ScanEvent.Found -> println("${event.advertisement.name} ${event.advertisement.identifier} rssi=${event.advertisement.rssi}")
                    is ScanEvent.Failed -> error(event.error)
                }
            }
    }
}
```

Or use the shared helper:

```kotlin
import com.atruedev.kmpble.scanner.scanBatch
import kotlin.time.Duration.Companion.seconds

val ads = BlueZScanner { }.use { it.scanBatch(limit = 20, timeout = 10.seconds) }
ads.forEach { println(it) }
```

Run with Gradle:

```bash
./gradlew jvmRun  # if you wire a small main, or run from your app module
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `UnsupportedOperationException` from `Scanner { }` | Expected on CI / without opt-in | Use `BlueZScanner { }` or set `kmpble.bluez.enabled` |
| `ScanEvent.Failed` / D-Bus error | `bluetoothd` not running or no D-Bus permission | `sudo systemctl start bluetooth`; add user to `bluetooth` group |
| No devices despite hardware | Adapter powered off | `bluetoothctl power on` |
| Empty scan on hardware | Filter rejected or no LE devices nearby | Check logs for `SetDiscoveryFilter` warnings; confirm devices advertising nearby |

### SetDiscoveryFilter behavior

`BlueZScanner` calls `Adapter1.SetDiscoveryFilter` with `Transport=le` before
`StartDiscovery`. If the filter call fails, a `BleLogEvent.Warning` is emitted and
scanning continues with adapter defaults. Set `BleLogConfig.logger` to surface warnings.
StartDiscovery does not depend on a successful filter on typical BlueZ builds.

## CI note

GitHub Actions `jvmTest` does **not** require BlueZ. Conformance tests use
`FakeScanner`. Do not enable BlueZ in CI unless you attach a runner with an adapter.

## See also

- [ADR-0001: JVM Linux BlueZ](adr/ADR-0001-jvm-linux-bluez.md)
- [Platform Setup: Android](platform-setup-android.md)
- [Platform Setup: iOS](platform-setup-ios.md)
