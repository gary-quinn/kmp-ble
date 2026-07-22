# Module kmp-ble-mesh

Kotlin Multiplatform BLE Mesh library providing a coroutine-based API for Bluetooth Mesh networking across Android, iOS, and JVM.

## Core capabilities

- BLE Mesh provisioning (PB-ADV and PB-GATT bearers)
- GATT Proxy protocol for smartphone mesh participation
- Mesh network management (keys, addresses, nodes)
- Foundation models (Configuration Server/Client, Health Server/Client)
- Standard models (Generic OnOff, Generic Level, Sensor)
- Vendor model support
- Network state persistence

## Getting started

Add the dependency to your KMP module:

```kotlin
commonMain.dependencies {
    implementation("com.atruedev:kmp-ble:<version>")
    implementation("com.atruedev:kmp-ble-mesh:<version>")
}
```
