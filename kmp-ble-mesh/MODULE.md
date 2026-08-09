# Module kmp-ble-mesh

> **STATUS: EXPERIMENTAL -- NOT PART OF THE 1.0 SCOPE**
>
> This module is a work-in-progress research prototype. It is **not** published
> to Maven Central and **not** covered by the kmp-ble 1.0 API stability
> commitment.
>
> **Known critical issue:** the pure-Kotlin P-256 ECDH implementation
> (`P256Ecdh.kt`) has unverified field arithmetic and does not satisfy ECDH
> commutativity (`sharedSecret(d1, Q2) != sharedSecret(d2, Q1)`). The failure
> is documented in `P256EcdhTest` but not yet fixed. Any mesh security that
> relies on this implementation must be treated as broken until it is
> rewritten and verified against NIST P-256 test vectors.
>
> Do not use this module in production. Consider B/C migration (platform
> crypto via expect/actual, or a vetted library) before relying on mesh
> security.

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

> `kmp-ble-mesh` is **not published** to Maven Central (see status note above).
> It can only be consumed as a `project(":kmp-ble-mesh")` source dependency
> within this repository. Published consumers should wait for a future
> release that addresses the crypto issue.

```kotlin
commonMain.dependencies {
    implementation(project(":kmp-ble-mesh"))
}
```
