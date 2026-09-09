# Verification matrix

Canonical L1/L2 contracts for kmp-ble. Agents **must** run the commands for every surface they touch and attach evidence (test output, build log, or short note) before marking work done.

Maintained with [ADR-0002](../adr/ADR-0002-verification-first-agent-assisted-development.md).

ASCII only in this repo (see `AGENTS.md`).

## Surface -> command -> evidence

| Surface touched | Agent MUST run | Evidence artifact |
| --- | --- | --- |
| `src/commonMain/**` (core) | `./gradlew iosSimulatorArm64Test` **or** `./gradlew jvmTest` (prefer both when concurrency/state machine touched) | Tests pass |
| `src/jvmMain/**` (BlueZ scanner/peripheral) | `./gradlew jvmTest` (includes `BlueZScannerLifecycleTest`, `BlueZPeripheralLifecycleTest` with fake sessions) | Tests pass |
| `src/commonMain/**/gatt/**`, `**/peripheral/**`, concurrency | `./gradlew jvmTest` | Lincheck / concurrency tests pass |
| `src/androidMain/**`, Android quirks host logic | `./gradlew testAndroidHostTest` | Host tests pass |
| `src/androidDeviceTest/**` or Android framework integration | `./gradlew connectedAndroidDeviceTest` (emulator/device) | Instrumented tests pass |
| `src/iosMain/**`, `src/appleMain/**` | `./gradlew compileKotlinIosSimulatorArm64` + `./gradlew iosSimulatorArm64Test` | Compile + tests pass |
| `kmp-ble-profiles/**` | `./gradlew :kmp-ble-profiles:allTests` (or module equivalent `*Test` tasks available in the tree) | Module tests pass |
| `kmp-ble-dfu/**` | `./gradlew :kmp-ble-dfu:allTests` | Module tests pass |
| `kmp-ble-codec/**` | `./gradlew :kmp-ble-codec:allTests` | Module tests pass |
| `kmp-ble-codec-serialization/**` | `./gradlew :kmp-ble-codec-serialization:allTests` | Module tests pass |
| `kmp-ble-quirks/**` | `./gradlew testAndroidHostTest` (quirks covered via host + SPI) | Host tests pass |
| `sample/**`, `sample-android/**`, `sample-quickstart/**`, `sample-quickstart-android/**` | Match CI sample job: compile common + Android (and iOS compile when iOS sample paths touched) | Compile clean |
| `ARCHITECTURE.md`, `docs/adr/**`, public API KDoc | Human review; add/update ADR when one-way door | ADR or review note |
| `.github/workflows/**`, branch protection, publish config | **Human review only** | -- |
| Physical BLE / GATT Lab E2E (`TESTING.md` manual checklist) | **Human only** | Human checklist; agents never claim this |

If a Gradle task name differs slightly on the branch you land on, use the closest existing module test task and record the exact command in the PR evidence.

## Commands (quick reference)

```sh
# L1 -- common / Fake tests on iOS simulator
./gradlew iosSimulatorArm64Test --no-daemon

# L1 -- Android host (JVM, no device)
./gradlew testAndroidHostTest --no-daemon

# L1 -- JVM concurrency / Lincheck
./gradlew jvmTest --no-daemon

# L1 -- Android instrumented (needs emulator/device)
./gradlew connectedAndroidDeviceTest --no-daemon

# L1 -- extension modules (when those trees are touched)
./gradlew :kmp-ble-codec:allTests :kmp-ble-codec-serialization:allTests :kmp-ble-profiles:allTests :kmp-ble-dfu:allTests --no-daemon

# L1 / L2 -- sample compile (mirror CI `sample` job)
./gradlew :sample:compileCommonMainKotlinMetadata :sample-quickstart:compileCommonMainKotlinMetadata \
  :sample:compileAndroidMain :sample-android:compileDebugKotlin \
  :sample-quickstart:compileAndroidMain :sample-quickstart-android:compileDebugKotlin --no-daemon
```

## Verification layers

| Layer | Tool | When |
| --- | --- | --- |
| **L1 library** | Gradle Fake/host/Lincheck/iOS common / instrumented | Agent before "done"; human anytime |
| **L2 CI** | GitHub Actions `ci.yml` | Every PR / main |
| **L3 human E2E** | `TESTING.md` physical BLE checklist | Releases / hardware-sensitive changes |

## "Done" checklist

1. Identify touched surfaces from the table above.
2. Run every required command; attach evidence on the PR or agent report.
3. Do **not** mark complete on compile-only when behavior or public API changed.
4. Do **not** claim physical BLE / GATT Lab verification.
5. Public API, new module, or expect/actual boundary changes: stop for human ADR / Gary first.
