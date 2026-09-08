# BLE Quickstart

Minimal Compose Multiplatform sample for [kmp-ble](../): scan, tap, connect, read or observe a characteristic, disconnect.

- **Display name:** BLE Quickstart
- **Bundle ID (iOS) / applicationId (Android):** `com.atruedev.kmpble.quickstart`

This module mirrors the lifecycle in [BleQuickstart.kt](../sample/src/commonMain/kotlin/com/atruedev/kmpble/sample/BleQuickstart.kt) with a single-screen UI and no ViewModel.

## Golden path

1. **Scan** nearby connectable peripherals
2. **Tap** a device in the list
3. **Connect** and wait for service discovery
4. **Read or observe** the Heart Rate measurement (0x180D/0x2A37) when present, otherwise the first notifiable or readable characteristic
5. **Disconnect** and return to the scanner

## Build and run

```bash
# Android (physical device recommended)
./gradlew :sample-quickstart-android:installDebug

# iOS framework (wire MainViewController() from SwiftUI)
./gradlew :sample-quickstart:linkDebugFrameworkIosSimulatorArm64
```

On iOS, host `MainViewController()` from your Xcode target the same way GATT Lab hosts `KmpBleSample`.

## Fake BLE mode (no radio)

For automation, emulators, and CI, inject `FakeQuickstartBle` (or set
`QuickstartConfig.useFakeBle` and launch `App`):

```kotlin
// Explicit factory injection (preferred for tests)
App(ble = FakeQuickstartBle)

// Runtime toggle for manual demo runs
QuickstartConfig.useFakeBle = true
App()
```

`FakeQuickstartBle` uses `FakeScanner` and `FakePeripheral` directly. Connect never
calls `toPeripheral()` on fake ads. `RealQuickstartBle` uses platform `Scanner` and
`toPeripheral()` for physical devices.

### UI smoke test (Android host / Robolectric)

```bash
./gradlew :sample-quickstart-android:testDebugUnitTest
```

The `QuickstartScreenFakeUiTest` golden-path test runs without a BLE radio or
emulator. It is included in the root `./gradlew testAndroidHostTest` CI job via
`:sample-quickstart-android:testDebugUnitTest`.

Stable Compose `testTag`s for the happy path live in `QuickstartTestTags`
(`quickstart_scan_row`, `quickstart_session`, `quickstart_value`,
`quickstart_disconnect`, `quickstart_nearby`, `quickstart_permission`).

## Related docs

- [GETTING_STARTED.md](../GETTING_STARTED.md) - add kmp-ble to your own project
- [BleQuickstart.kt](../sample/src/commonMain/kotlin/com/atruedev/kmpble/sample/BleQuickstart.kt) - code-only walkthrough
- [sample/](../sample/) - full GATT Lab demo app
