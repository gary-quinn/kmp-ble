# Module kmp-ble

Kotlin Multiplatform BLE library providing a coroutine-based API for Bluetooth Low Energy communication across Android, iOS, and JVM.

## Core capabilities

- BLE scanning with Flow-based advertisement streams
- Connection lifecycle management with typed state machines
- GATT read/write/notify operations with structured concurrency
- Per-peripheral serial execution via `limitedParallelism(1)` dispatcher

## Getting started

Add the dependency to your KMP module:

```kotlin
commonMain.dependencies {
    implementation("com.atruedev:kmp-ble:<version>")
}
```
