# Contributing to kmp-ble

Thanks for your interest in contributing! This guide covers everything you need to get started.

---

## Getting Started

### Prerequisites

- **JDK 21+**
- **Android SDK** (API 33+, install via Android Studio)
- **Xcode 16+** (for iOS targets, macOS only)
- **Kotlin 2.3.0+** (managed by Gradle wrapper)

### Build

```bash
# Clone
git clone https://github.com/atruedeveloper/kmp-ble.git
cd kmp-ble

# Build all targets
./gradlew build

# Android tests only
./gradlew androidHostTest

# iOS simulator tests only (macOS required)
./gradlew iosSimulatorArm64Test
```

### Project Structure

```
kmp-ble/
├── src/
│   ├── commonMain/          # Shared API, state machine, testing fakes
│   ├── commonTest/          # Shared tests (run on all platforms)
│   ├── androidMain/         # Android BluetoothGatt, BluetoothLeScanner
│   ├── androidHostTest/     # Android-specific tests
│   └── iosMain/             # CoreBluetooth CBCentralManager, CBPeripheralManager
├── sample/                  # Compose Multiplatform sample app
├── sample-android/          # Android-only sample app
└── iosApp/                  # iOS sample app (Xcode project)
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the internals fit together.

---

## Ways to Contribute

### 1. Device Quirk Entries (Easiest)

The device quirk registry (`src/androidMain/.../internal/DeviceQuirks.kt`) contains workarounds for Android OEM-specific BLE behavior. If you've encountered a device-specific BLE issue, adding a quirk entry is the simplest way to contribute.

**How to add a quirk:**

1. Open `src/androidMain/kotlin/com/atruedev/kmpble/internal/DeviceQuirks.kt`
2. Add your device to the relevant quirk list with the manufacturer, model, and (optionally) display string
3. Add a test in `src/androidHostTest/kotlin/com/atruedev/kmpble/internal/DeviceQuirksTest.kt`
4. In your PR description, include:
   - Device manufacturer and model
   - Android version
   - The BLE issue you observed
   - How the quirk fixes it

### 2. Bug Reports

File an [issue](https://github.com/atruedeveloper/kmp-ble/issues) with:

- **Platform**: Android (API level, device model) or iOS (version, device)
- **kmp-ble version**
- **Minimal reproduction** or code snippet
- **Expected vs actual behavior**
- **Logs** (if available, enable via `BleLogConfig`)

### 3. Feature Requests

Open an [issue](https://github.com/atruedeveloper/kmp-ble/issues) describing:

- Your use case (what are you building?)
- What you need from kmp-ble that's missing
- How you're currently working around it (if applicable)

### 4. Code Contributions

For anything beyond quirk entries, please **open an issue first** to discuss the approach before writing code. This avoids wasted effort on changes that don't align with the project direction.

---

## Pull Request Process

### Before You Start

1. Check [existing issues](https://github.com/atruedeveloper/kmp-ble/issues) to avoid duplicate work
2. For non-trivial changes, open an issue to discuss the approach
3. Fork the repository and create a branch from `main`

### Writing Code

- **Follow existing patterns.** Read the surrounding code before adding new code. The codebase uses consistent patterns (sealed interfaces for errors, `@DslMarker` for builders, `limitedParallelism(1)` for serialization).
- **Write tests.** New features need tests in `commonTest/`. Platform-specific behavior needs tests in `androidHostTest/` or `iosTest/`.
- **Keep changes focused.** One PR per logical change. Don't mix refactoring with new features.
- **No unnecessary changes.** Don't reformat code you didn't change, add comments to existing code, or rename things unrelated to your PR.

### PR Title Convention

PR titles **must** follow [Conventional Commits](https://www.conventionalcommits.org/) format. CI will reject PRs that don't match.

```
feat: add extended advertising support
fix: handle null RSSI on Samsung devices
refactor: simplify GATT operation queue
chore: update Kotlin to 2.3.1
```

| Prefix | When to use |
|--------|-------------|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `docs` | Documentation only |
| `test` | Adding or updating tests |
| `chore` | Build, CI, dependencies, tooling |
| `ci` | CI/CD changes |
| `revert` | Reverting a previous change |

A changelog label (`added`, `changed`, or `fixed`) is **applied automatically** based on your PR title — no manual labeling needed. These labels drive the auto-generated [CHANGELOG](CHANGELOG.md) on each release.

> **Maintainers:** Ensure the repository has labels `added` (green), `changed` (blue), and `fixed` (orange). The CI auto-creates them if missing, but without colors or descriptions.

### Submitting

1. Ensure all tests pass: `./gradlew build`
2. Write a PR title following the conventional commit format above
3. Reference the related issue (e.g., "Fixes #42")
4. PRs are reviewed within 72 hours

### What We Look For in Review

- **Correctness**: Does it handle edge cases? BLE is full of them.
- **Thread safety**: All mutable state accessed from the peripheral's serialized dispatcher? No shared mutable state across peripherals?
- **Platform parity**: If it works on Android, does it work on iOS (or is the limitation documented)?
- **Testability**: Can this be tested with FakePeripheral/FakeScanner without hardware?
- **API consistency**: Does the public API follow the existing patterns (suspend functions for one-shot ops, Flows for streams, StateFlows for observable state)?

---

## Code Conventions

- **Kotlin style**: Official Kotlin coding conventions
- **Explicit API mode** is enabled — all public declarations need visibility modifiers and return types
- **`internal`** for anything not part of the public API
- **Sealed interfaces** over sealed classes for error types (composability)
- **`expect/actual`** only for platform bridges — shared logic stays in `commonMain`
- **No `runBlocking`** in library code
- **No mocks** in tests — use the Fake* implementations (FakePeripheral, FakeScanner, FakeGattServer, FakeAdvertiser, FakeL2capChannel)

---

## Testing

### Running Tests

```bash
# All platforms
./gradlew build

# Android only
./gradlew androidHostTest

# iOS simulator only (macOS)
./gradlew iosSimulatorArm64Test
```

### Writing Tests

Tests go in `commonTest/` unless they're platform-specific. Use the Fake* classes:

```kotlin
@Test
fun `read returns characteristic value`() = runTest {
    val peripheral = FakePeripheral {
        identifier = Identifier("test")
        service("180A") {
            characteristic("2A29") {
                properties(read = true)
                onRead { "TestManufacturer".encodeToByteArray() }
            }
        }
    }

    peripheral.connect()
    val value = peripheral.read(peripheral.findCharacteristic(...)!!)
    assertEquals("TestManufacturer", value.decodeToString())
}
```

All BLE logic should be testable without hardware. If your change can't be tested with Fake* classes, that's a design signal worth discussing.

---

## Questions?

Open an [issue](https://github.com/atruedeveloper/kmp-ble/issues) or start a [discussion](https://github.com/atruedeveloper/kmp-ble/discussions). No question is too basic.
