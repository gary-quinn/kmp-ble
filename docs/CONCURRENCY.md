# Concurrency Policy - kmp-ble

This document defines kmp-ble's concurrency model, approved synchronization primitives, and migration path from legacy patterns. It is the single source of truth for thread-safety decisions across all platform modules.

---

## Core Model: Single-Threaded Dispatcher + Atomics

kmp-ble uses a **structured concurrency** model built on Kotlin coroutines:

- All BLE callback processing funnels through `Dispatchers.IO.limitedParallelism(1)` - a single-threaded dispatcher that serializes platform events into sequential coroutine suspension points.
- Shared mutable state that crosses coroutine boundaries uses **kotlinx-atomicfu** atomics.
- Public API surfaces return `Flow<T>`, `StateFlow<T>`, or suspend functions - never raw callbacks.

```
Platform callback (Android Binder / iOS CoreBluetooth)
        |
        v
limitedParallelism(1) dispatcher  <-- serializes all events
        |
        v
atomicfu state fields            <-- safe cross-coroutine reads
        |
        v
StateFlow / SharedFlow           <-- public API emission
```

## Approved Primitives

| Primitive | Use case | Example |
|-----------|----------|---------|
| `kotlinx.atomicfu.AtomicRef<T>` | Single-field atomic updates | Connection state, configuration flags |
| `kotlinx.atomicfu.AtomicInt` / `AtomicLong` | Counters, sequence numbers | GATT operation sequence, retry counters |
| `Dispatchers.IO.limitedParallelism(1)` | Serial event processing | BLE callback dispatch, GATT queue |
| `StateFlow<T>` | Read-only state emission | Connection.state, Scanner.scanState |
| `Mutex` (kotlinx.coroutines.sync) | **iOS only** - CoreBluetooth callback serialization when limitedParallelism is unavailable | IosPeripheralManagerDelegate |
| `ConcurrentHashMap` (JVM) | Android-only concurrent map access | GATT handle cache, descriptor cache |

## Forbidden Patterns

These MUST NOT appear anywhere in kmp-ble source:

| Pattern | Reason | Alternative |
|---------|--------|-------------|
| `@Volatile` | JVM-only, no KMP equivalent, data races on non-trivial types | `atomicfu` |
| `synchronized` / `@Synchronized` | Blocks coroutine threads, deadlock risk | `Mutex` (iOS), atomicfu (common) |
| `GlobalScope` / `GlobalScope.launch` | Unbounded lifecycle, leaks | `CoroutineScope` tied to lifecycle |
| `ReentrantLock` / `java.util.concurrent.locks.*` | JVM-only, anti-KMP | `Mutex` |
| `runBlocking` in shared code | Blocks platform thread, deadlock risk | `runTest` (tests), `runBlocking` only in `jvmTest`/`androidHostTest` |
| `Thread` / `Executors` in shared code | Platform-specific, no KMP | Coroutine dispatchers |

## Platform-Specific Notes

### Android (androidMain)

- **BLE stack runs on Binder threads.** All `BluetoothGattCallback` methods arrive on arbitrary Binder threads. Use `limitedParallelism(1)` dispatcher to serialize.
- **@Volatile is acceptable ONLY inside Android-specific internal classes** (e.g., `AndroidGattBridge`) where the field is a single reference (`BluetoothGatt?`) and the kotlinx-atomicfu Gradle plugin isn't configured for that module.
- **Migration priority:** `@Volatile` fields in `androidMain` should be migrated to atomicfu when practical. Current count: 9 remaining (see tracking issue #507).

### iOS (iosMain)

- **CoreBluetooth delegates run on libdispatch main queue by default.** kmp-ble dispatches to a background queue where possible.
- **Mutex is allowed in iosMain** as a pragmatic concession - Swift/Kotlin interop makes atomicfu challenging in some delegate wrappers.
- **No @Volatile on iOS** - the Kotlin/Native `@Volatile` semantics differ from JVM; use atomicfu instead.

## Migration Guide: @Volatile → atomicfu

### Before
```kotlin
@Volatile
private var state: ConnectionState = ConnectionState.Disconnected

@Volatile
private var pendingCount: Int = 0
```

### After
```kotlin
private val _state = atomic(ConnectionState.Disconnected)
private var state: ConnectionState by _state

private val _pendingCount = atomic(0)
private var pendingCount: Int by _pendingCount
```

### Steps
1. Add `kotlinx-atomicfu` dependency to module `build.gradle.kts`
2. Apply `kotlinx.atomicfu` Gradle plugin
3. Replace `@Volatile var x: T` with `private val _x = atomic(initial); var x: T by _x`
4. Replace direct reads/writes with property delegation (reads compile to `.value`, writes to `.value =`)
5. Verify with `./gradlew :module:compileKotlinJvm :module:compileKotlinIosArm64`

## When to Use What

```
Is the state shared across coroutine boundaries?
├── No  → Plain val/var is fine within a single coroutine
└── Yes → Is it a single field?
    ├── Yes → atomicfu
    └── No  → Does it need compound updates?
        ├── Yes → Mutex + atomicfu for the composite state
        └── No  → atomicfu for each field independently
```

## Enforcement

- CI checks: `grep -rPn "(GlobalScope|synchronized\b|@Volatile|ReentrantLock|runBlocking)" src/ --include='*.kt' | grep -v testdata | grep -v "docs/CONCURRENCY.md"` runs on every PR
- Architectural Watchdog audits merged commits for violations
- Violations open CRITICAL priority bugs

## References

- [ARCHITECTURE.md](ARCHITECTURE.md) - State machine and overall design
- [#507](https://github.com/gary-quinn/kmp-ble/issues/507) - @Volatile migration tracking
- [#514](https://github.com/gary-quinn/kmp-ble/issues/514) - ObservationPersistence @Volatile migration
- [kotlinx-atomicfu](https://github.com/Kotlin/kotlinx-atomicfu)
