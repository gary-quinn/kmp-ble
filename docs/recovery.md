# Recovery Patterns

Production BLE apps must handle disconnects, transient failures, and platform-specific quirks. This guide covers battle-tested recovery patterns using kmp-ble's built-in concurrency and state machinery.

## Table of Contents

- [Connection Loss Recovery](#connection-loss-recovery)
- [State Machine Transitions](#state-machine-transitions)
- [GATT Operation Retry](#gatt-operation-retry)
- [Pairing Failure Recovery](#pairing-failure-recovery)
- [Platform-Aware Timeouts](#platform-aware-timeouts)
- [Foreground vs Background](#foreground-vs-background)

## Connection Loss Recovery

### Exponential Backoff

Unexpected disconnects are inevitable. Use `ReconnectionStrategy.ExponentialBackoff` to avoid thundering-herd reconnection storms:

```kotlin
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import kotlin.time.Duration.Companion.seconds

val options = ConnectionOptions(
    reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
        initialDelay = 1.seconds,
        maxDelay = 30.seconds,
        maxAttempts = 10,
    ),
)
peripheral.connect(options)
```

The delay doubles with each attempt: 1s -> 2s -> 4s -> 8s -> 16s -> 30s (capped). After `maxAttempts` failures, reconnection stops and `peripheral.state` transitions to `Disconnected.ByError`.

### Linear Backoff for Constrained Devices

Battery-constrained IoT sensors may not tolerate aggressive reconnect attempts. Use `LinearBackoff` with a longer fixed delay:

```kotlin
ReconnectionStrategy.LinearBackoff(
    delay = 5.seconds,
    maxAttempts = 3,
)
```

### Manual Reconnection

For full control, set `ReconnectionStrategy.None` and observe `peripheral.state`:

```kotlin
import com.atruedev.kmpble.connection.State
import kotlinx.coroutines.flow.filter

peripheral.state
    .filter { it is State.Disconnected.ByRemote || it is State.Disconnected.ByError }
    .collect { state ->
        val reason = when (state) {
            is State.Disconnected.ByRemote -> "remote disconnected"
            is State.Disconnected.ByError -> "error: ${state.error}"
            else -> "unknown"
        }
        log("Reconnecting after $reason")
        delay(backoff.next())
        peripheral.connect()
    }
```

### Which Strategy to Use

| Scenario | Strategy | Rationale |
|----------|----------|-----------|
| Medical device (data gaps unacceptable) | `ExponentialBackoff(maxAttempts=10, maxDelay=30s)` | Aggressive recovery |
| Fitness tracker (workout session) | `ExponentialBackoff(maxAttempts=5, maxDelay=15s)` | Fast recovery, bounded |
| IoT sensor (battery-constrained) | `LinearBackoff(delay=5s, maxAttempts=3)` | Conservative, power-aware |
| Consumer audio (user-initiated) | `ExponentialBackoff(maxAttempts=3, maxDelay=10s)` | Brief recovery, user retries |
| Custom control | `None` + manual state observation | Full flexibility |

## State Machine Transitions

kmp-ble exposes 14 states across four phases. Understanding the transition graph helps diagnose field issues.

### Observing States

```kotlin
import com.atruedev.kmpble.connection.State
import kotlinx.coroutines.flow.collect

peripheral.state.collect { state ->
    when (state) {
        State.Connecting.Transport -> log("BLE link establishing")
        State.Connecting.Authenticating -> log("Pairing/bonding in progress")
        State.Connecting.Discovering -> log("GATT service discovery")
        State.Connecting.Configuring -> log("MTU negotiation, CCCD setup")
        State.Connected.Ready -> log("Ready for GATT operations")
        State.Connected.BondingChange -> log("Bond state changed while connected")
        State.Connected.ServiceChanged -> log("Remote GATT database changed - rediscover")
        State.Disconnecting.Requested -> log("Local disconnect initiated")
        State.Disconnecting.Error -> log("Disconnecting due to error")
        State.Disconnected.ByRequest -> log("Clean local disconnect completed")
        State.Disconnected.ByRemote -> log("Remote device disconnected")
        is State.Disconnected.ByError -> log("Error: ${state.error}")
        State.Disconnected.ByTimeout -> log("Connection timed out")
        State.Disconnected.BySystemEvent -> log("Bluetooth off or app backgrounded")
    }
}
```

### What to Check at Each Step

**Connecting -> Connected.Ready**: Normal. Services are discovered, CCCDs configured. Begin GATT operations.

**Connecting -> Disconnected.ByError**: Check `state.error`. Common causes:
- Device out of range (`ConnectionError.Timeout`)
- Bonding rejected (`BondError.*`)
- GATT error 133 (`ConnectionError.GattError`)

**Connected.Ready -> Connected.ServiceChanged**: The remote GATT database changed. Any cached `Characteristic` or `Descriptor` references are stale. Call `peripheral.refreshServices()` and re-acquire references.

**Connected.* -> Disconnected.BySystemEvent**: Bluetooth turned off or app backgrounded on iOS. Present a user prompt to re-enable Bluetooth. Do not attempt reconnection until `BluetoothAdapter.state` returns `PoweredOn`.

**Disconnected.ByRequest**: User-initiated disconnect. Do not reconnect.

### Health Check Pattern

```kotlin
suspend fun monitorHealth(peripheral: Peripheral) {
    val startTime = currentTimeMillis()
    peripheral.state.collect { state ->
        when {
            state is State.Connecting && elapsedSince(startTime) > 60.seconds ->
                log("WARN: connecting for >60s - possible range or bonding issue")
            state is State.Disconnected.ByTimeout ->
                log("ERROR: connection timed out - check range and device availability")
            state is State.Disconnected.BySystemEvent ->
                log("WARN: system-level disconnect - Bluetooth may be off")
        }
    }
}
```

## GATT Operation Retry

### Transparent Reconnection via observeValues()

`observeValues()` suspends during disconnects and resumes transparently on reconnection. No error handling needed for transient link losses:

```kotlin
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.flow.collect

// Survives disconnects - suspends silently, resumes after reconnect
peripheral.observeValues(char, BackpressureStrategy.Latest).collect { data ->
    process(data)
}
```

### Explicit Retry for Read/Write Operations

Single-shot operations (read, write) need explicit retry logic:

```kotlin
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.connection.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

suspend fun Peripheral.writeWithRetry(
    characteristic: Characteristic,
    data: ByteArray,
    maxRetries: Int = 3,
) {
    repeat(maxRetries) { attempt ->
        try {
            write(characteristic, data, WriteType.WithResponse)
            return // success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            // Check state before retrying
            when (state.value) {
                is State.Disconnected.ByRequest -> throw e // user disconnected
                is State.Connected.Ready -> delay(200) // transient GATT error
                else -> {
                    // Wait for reconnection
                    state.first { it is State.Connected.Ready }
                    delay(200) // settle
                }
            }
        }
    }
}
```

### When NOT to Retry

- **WriteWithoutResponse**: The stack does not confirm delivery. Retrying may send duplicates.
- **Bonding failure**: Retrying bond operations often escalates the problem. See [Pairing Failure Recovery](#pairing-failure-recovery).
- **MTU negotiation failure**: The device doesn't support the requested MTU. Fall back to the default (23 bytes).
- **Descriptor write with `CCCD`**: kmp-ble handles CCCD writes internally. Don't manually retry.

## Pairing Failure Recovery

### Detection

Pairing failures surface through `state`:

```kotlin
peripheral.state.collect { state ->
    when (state) {
        is State.Disconnected.ByError -> {
            if (state.error is BondError) {
                recoverFromBondFailure(state.error)
            }
        }
        // Also check disconnected.substates
        else -> {}
    }
}
```

### Recovery Paths

**1. Bond Rejected by User**

User dismissed the system pairing dialog. Do not retry with `BondingPreference.Required`. Present a dialog explaining why bonding is needed, then try with `IfRequired`:

```kotlin
suspend fun reconnectAfterBondRejection(peripheral: Peripheral) {
    // User must explicitly consent
    val didConsent = showBondExplanation()
    if (didConsent) {
        peripheral.connect(ConnectionOptions(
            bondingPreference = BondingPreference.Required,
        ))
    }
}
```

**2. Bond Lost (Device Forgot Bond)**

When a peripheral loses its bond (common after firmware update), the connection falls back to an unbonded state:

```kotlin
suspend fun recoverLostBond(peripheral: Peripheral) {
    // Remove the stale bond on our side
    peripheral.removeBond()
    // Reconnect with fresh bond
    peripheral.connect(ConnectionOptions(
        bondingPreference = BondingPreference.Required,
    ))
}
```

**3. PIN/Passkey Mismatch**

The peripheral's PIN changed. Clear old bonds, notify user:

```kotlin
suspend fun recoverPasskeyMismatch(peripheral: Peripheral) {
    peripheral.removeBond()
    showPasskeyPrompt()
    peripheral.connect(ConnectionOptions(
        bondingPreference = BondingPreference.Required,
    ))
}
```

**4. OOB Data Expired (Android Only)**

OOB (Out-of-Band) pairing data has a short validity window:

```kotlin
suspend fun recoverOobExpired(peripheral: Peripheral) {
    val freshOobData = fetchOobFromBackend()
    peripheral.connect(ConnectionOptions(
        bondingPreference = BondingPreference.Required,
        pairingHandler = OobPairingHandler(freshOobData),
    ))
}
```

### Pairing Recovery Checklist

| Symptom | Action |
|---------|--------|
| Bond rejected by user | Explain, then retry with `BondingPreference.IfRequired` |
| Bond lost (device side) | `removeBond()` + reconnect with `Required` |
| PIN mismatch | `removeBond()` + prompt for new PIN |
| OOB expired | Fetch fresh OOB data from backend |
| Repeated bond failures | `removeBond()`, skip bonding (`None`), notify support |
| Samsung-specific quirks | QuirkRegistry auto-detects; kmp-ble handles transparently |

## Platform-Aware Timeouts

### Connection Timeouts

```kotlin
// Medical devices: slow bonding, encryption negotiation
ConnectionOptions(timeout = 60.seconds)

// Consumer devices: should connect quickly
ConnectionOptions(timeout = 20.seconds)

// IoT sensors: battery-constrained, give up fast
ConnectionOptions(timeout = 15.seconds)
```

### GATT Operation Timeouts

On Android, slow Bluetooth stacks (particularly Samsung) may delay GATT operations:

```kotlin
ConnectionOptions(
    gattOperationTimeout = 30.seconds, // increased for slow devices
    timeout = 60.seconds,
)
```

### Recipe Presets

Use `ConnectionRecipe` presets as starting points:

```kotlin
import com.atruedev.kmpble.connection.ConnectionRecipe

peripheral.connect(ConnectionRecipe.MEDICAL)       // 60s timeout, 10 retries
peripheral.connect(ConnectionRecipe.FITNESS)       // 30s timeout, 5 retries
peripheral.connect(ConnectionRecipe.IOT)           // 15s timeout, 3 retries
peripheral.connect(ConnectionRecipe.CONSUMER)      // 20s timeout, 3 retries
```

### Platform-Specific Timeout Behaviors

| Platform | Quirk |
|----------|-------|
| Android - Samsung | GATT error 133 on concurrent operations. Serialize via `Peripheral` (automatic). |
| Android - Pixel | `BluetoothGatt.connect()` may take 10+ seconds. Use generous timeouts. |
| iOS - All | CoreBluetooth timeout is ~10 seconds. Values above this have no effect. |
| iOS - Background | System may delay connection by several seconds. |

## Foreground vs Background

### Android Background BLE

Android limits background operations. Use a foreground service for long-running connections:

```kotlin
class BleForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // BLE operations continue
        return START_STICKY
    }
}
```

### iOS Background BLE

On iOS, declare `bluetooth-central` and `bluetooth-peripheral` in `Info.plist`. State restoration preserves connections across app termination:

```kotlin
import com.atruedev.kmpble.connection.StateRestorationConfig

KmpBle.init(
    context = this,
    stateRestoration = StateRestorationConfig(
        restoreIdentifier = "com.example.ble.central",
    ),
)
```

When the system relaunches your app after a BLE event, `KmpBle` restores peripherals automatically. Re-subscribe to `peripheral.state` to re-establish your observers.
