# Choosing your data layer

kmp-ble offers three ways to move bytes between your app and a peripheral.
Pick based on payload shape and how you want failures surfaced.

## Decision matrix

| Your data | Module | API surface | Missing characteristic | Parse / decode failure | GATT / connection failure |
|-----------|--------|-------------|------------------------|------------------------|---------------------------|
| Bluetooth SIG profile (Heart Rate, Battery, Device Info, ...) | `kmp-ble-profiles` | `heartRateMeasurements()`, `readBatteryLevel()`, ... | `null` (reads) or `emptyFlow()` (observations) | `mapNotNull` drops unparseable notifications; reads return `null` when parse fails | `BleException` from underlying `read` / `observeValues` |
| `@Serializable` app types (CBOR on wire) | `kmp-ble-codec` + `kmp-ble-codec-serialization` | `cborCodec<T>()`, `readAs` / `writeAs` / `observeAs`, `L2capChannel.writeFramed` | `Result.failure(CharacteristicNotFoundException)` or `PeripheralNotReadyException` via `readAs` | `BleDecoder.decode` throws; `readAs` wraps as `DecodeFailureException` in `Result.failure`; L2CAP `onDecodeFailure` callback | `Result.failure(...)` for codec-layer errors; `BleException` for raw `read`/`write` |
| Proprietary / manual binary | `kmp-ble` (core) or `kmp-ble-codec` | `read`/`write`/`observeValues` on `ByteArray`, or custom `BleDecoder`/`BleEncoder` | `findCharacteristic(...) == null` (you handle) | Custom decoder throws (or return nullable type); `observeValues(char, decoder)` propagates decode throws | `BleException` with typed `BleError` |

## When to use profiles

Use **kmp-ble-profiles** when the device implements a standard GATT service from
the Bluetooth SIG assigned numbers (Heart Rate 0x180D, Battery 0x180F, Glucose
0x1808, etc.).

Profiles wrap core `Peripheral` operations with parsers aligned to each
characteristic's specification. Extension functions return typed values and
treat "service not present" as a normal outcome:

```kotlin
// Absent service -> emptyFlow(), not an error
peripheral.heartRateMeasurements().collect { measurement ->
    println("BPM: ${measurement.heartRate}")
}

// Absent characteristic -> null
val level = peripheral.readBatteryLevel() // Int? 
```

You still handle `BleException` for connection and GATT failures. Profiles do
not throw when a device simply lacks a service.

Build from source: see [Build from source](../README.md#build-from-source-extension-modules).

## When to use codec (+ serialization)

Use **kmp-ble-codec** when you define your own payload layout or want typed
GATT/L2CAP helpers without hand-rolling byte offsets everywhere.

- **`BleDecoder.decode`** throws on malformed input.
- **`Peripheral.read(char, decoder)`** propagates decode throws after a
  successful GATT read.
- **`Peripheral.readAs(serviceUuid, charUuid, decoder)`** returns
  `Result<T>` with sealed `PeripheralCodecException` subclasses for
  not-found, not-ready, and decode failures.

Add **kmp-ble-codec-serialization** when payloads are `@Serializable` and CBOR
on the wire is acceptable:

```kotlin
@Serializable
data class Reading(val timestampMs: Long, val celsius: Double)

val codec = cborCodec<Reading>()
val result = peripheral.readAs(serviceUuid, charUuid, codec)
result.onSuccess { render(it) }
    .onFailure { /* CharacteristicNotFoundException, DecodeFailureException, ... */ }
```

For L2CAP streams, pair `cborCodec<T>()` with `LengthPrefixFramer` via
`writeFramed` / `framedIncoming`. Decode failures on a stream invoke
`onDecodeFailure` without tearing down the channel.

Build from source: see [Build from source](../README.md#build-from-source-extension-modules).

## When to use raw ByteArray (core)

Use the **kmp-ble** core API when:

- The layout is proprietary and no profile exists.
- You want full control over parsing and error policy.
- You are prototyping before extracting a `BleDecoder`.

```kotlin
val value = peripheral.read(characteristic) // ByteArray; throws BleException
peripheral.observeValues(characteristic).collect { bytes ->
    if (!parseHeader(bytes)) return@collect // your policy
    handlePayload(bytes)
}
```

Define a custom `BleDecoder<T>` (in your app or in `kmp-ble-codec`) when the
layout stabilizes but still is not a SIG profile.

## Quick chooser

```
SIG-assigned service UUID?
  yes -> kmp-ble-profiles (null / emptyFlow for absence)
  no  -> Is the payload a @Serializable Kotlin type on CBOR?
          yes -> kmp-ble-codec-serialization (+ codec for framing/L2CAP)
          no  -> kmp-ble core ByteArray, or kmp-ble-codec with custom BleDecoder
```

## Related docs

- [API Quick Reference](api-quick-reference.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Typed Codec Layer section
- [STREAMS.md](../STREAMS.md) - L2CAP typed streams with codec + framing
