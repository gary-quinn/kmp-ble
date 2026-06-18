# L2CAP Subsystem Architecture

The L2CAP (Logical Link Control and Adaptation Protocol) subsystem provides
Connection-Oriented Channel (CoC) support for the kmp-ble library. L2CAP CoC
channels bypass GATT and offer stream-oriented communication with higher
throughput and lower latency -- ideal for firmware updates, bulk data transfer,
and any scenario where GATT's request/response model is insufficient.

## Dual Role

The subsystem has two roles, mirroring the Bluetooth L2CAP specification:

**Client (central-initiated):** The central opens an L2CAP channel to a known
PSM on the peripheral via `Peripheral.openL2capChannel(psm)`. This returns an
`L2capChannel` object that supports bidirectional streaming.

**Server (peripheral-side):** An application creates an `L2capListener` to
publish an L2CAP service. The OS assigns a PSM, which the app advertises
(e.g., via a GATT characteristic). Centrals connect to this PSM, and accepted
channels are delivered through the listener's `incoming` SharedFlow.

Client and server are fully decoupled -- a peripheral can open channels to
other devices while simultaneously publishing its own listener.

## Architecture Layers

```
+------------------------------------------------------------------+
|                        Public API (commonMain)                    |
|  Peripheral.openL2capChannel()  |  L2capListener() factory       |
|  L2capChannel interface        |  L2capListener interface        |
+------------------------------------------------------------------+
|                    Platform Implementations                       |
|  iosMain: IosL2capChannel,     |  iosMain: IosL2capListener      |
|           IosPeripheralL2cap   |                                  |
|  androidMain: AndroidL2cap...  |  androidMain: AndroidL2cap...   |
|  jvmMain: throws NotSupported  |  jvmMain: throws NotSupported   |
+------------------------------------------------------------------+
|                    Platform Bridge Layer                          |
|  iOS: CBL2CAPChannel, CBPeripheral.openL2CAPChannel()            |
|  Android: BluetoothSocket, BluetoothDevice.createL2capChannel() |
+------------------------------------------------------------------+
```

## Client-Side Flow

```
Peripheral.openL2capChannel(psm, secure, mtu)
    |
    +-- Platform impl (e.g., AndroidPeripheral.kt delegates to
    |   AndroidPeripheralL2cap.kt::openL2capChannelInternal)
    |       |
    |       +-- BluetoothDevice.createL2capChannel(psm) or
    |       |   createInsecureL2capChannel(psm)
    |       |
    |       +-- [blocking] socket.connect()
    |       |
    |       +-- wrap in AndroidL2capChannel(socket, psm, scope)
    |
    +-- Returns L2capChannel
            |
            +-- channel.write(data)  -- OutputStream bridge
            +-- channel.incoming     -- InputStream → Channel → Flow
            +-- channel.close()      -- closes socket + streams
```

### Channel Lifecycle

1. **Open:** `peripheral.openL2capChannel(psm)` -- platform creates socket/channel,
   connects, wraps in `L2capChannel` implementation.
2. **Active:** `write()` sends data; `incoming` flow emits received packets.
3. **Close:** `channel.close()` flushes, closes streams, completes `incoming` flow.
4. **Disconnect:** If the peripheral disconnects, channels are automatically
   closed as part of `onDisconnectCleanup()`.

## Server-Side Flow

```
val listener = L2capListener()            // No OS resources yet
listener.open(secure = true, mtu = null)  // OS publishes, assigns PSM
    |
    +-- Android: BluetoothAdapter.listenUsingL2capChannel()
    |   iOS: CBPeripheralManager.publishL2CAPChannel()
    |
    +-- Accept loop starts (dispatched to background)
    |       |
    |       +-- [blocking] serverSocket.accept()  -- Android
    |       +-- GCD dispatch queue accept           -- iOS
    |       |
    |       +-- wrap in L2capChannel, emit to incoming SharedFlow
    |
listener.incoming.collect { channel -> ... }
listener.close()  // stops accepting; existing channels unaffected
```

### Channel Ownership

Each `L2capChannel` emitted from `listener.incoming` is fully open and owned
by the consumer. The listener does not track or close accepted channels --
consumers must call `channel.close()` on each one.

## Platform Implementations

### commonMain (public API)

| File | Lines | Role |
|------|-------|------|
| `L2capChannel.kt` | 100 | Interface: mtu, psm, isOpen, incoming: Flow<ByteArray>, write(), close() |
| `L2capListener.kt` | 126 | Interface: psm, isOpen: StateFlow<Boolean>, incoming: SharedFlow<L2capChannel>, open(), close() |
| `L2capException.kt` | 64 | Sealed hierarchy: OpenFailed, WriteFailed, ChannelClosed, NotConnected, NotSupported, PublishFailed, InvalidState |

### iOS (CoreBluetooth)

| File | Lines | Role |
|------|-------|------|
| `IosL2capChannel.kt` | 194 | CBL2CAPChannel wrapper: NSInputStream/NSOutputStream → Channel→Flow bridging |
| `IosL2capListener.kt` | 236 | CBPeripheralManager.publishL2CAPChannel(): GCD dispatch queue accept loop |
| `IosPeripheralL2cap.kt` | 83 | Extension functions: openL2capChannelInternal, handleDidOpenL2CAPChannel, closeL2capChannels |
| `L2capListenerFactory.ios.kt` | 3 | `actual fun L2capListener()` → `IosL2capListener()` |

**Key iOS details:**
- MTU: CoreBluetooth does not expose the negotiated L2CAP MTU. Defaults to
  2048 bytes. Users can pass an explicit `mtu` parameter to `openL2capChannel()`
  when the peripheral's MTU is known.
- Security: `secure` flag on `openL2capChannel()` is ignored on iOS --
  CoreBluetooth determines encryption at the connection level.
- Listener: Shares the underlying `CBPeripheralManager` with `GattServer` and
  `Advertiser`. The `L2capListener` factory is independent of `GattServer`
  in the public API, but the platform handle is shared.

### Android (BluetoothAdapter / BluetoothDevice)

| File | Lines | Role |
|------|-------|------|
| `AndroidL2capChannel.kt` | 159 | BluetoothSocket wrapper: InputStream/OutputStream bridging |
| `AndroidL2capListener.kt` | 148 | BluetoothAdapter.listenUsingL2capChannel(): blocking accept loop |
| `BluetoothL2capSocket.kt` | 24 | Adapts BluetoothSocket to L2capSocket interface |
| `L2capSocket.kt` | 18 | Internal interface for socket abstraction |
| `AndroidPeripheralL2cap.kt` | 157 | Extension functions: openL2capChannelInternal, closeL2capChannels, phyToMask |
| `L2capListenerFactory.android.kt` | 5 | `actual fun L2capListener()` → `AndroidL2capListener()` |

**Key Android details:**
- MTU: Queried from `L2capSocket.maxTransmitPacketSize`, floored at 672 bytes.
  The `mtu` parameter to `openL2capChannel()` is ignored on Android.
- Security: `secure=false` uses `createInsecureL2capChannel()`;
  `secure=true` (default) uses `createL2capChannel()`.
- Threading: Read loop runs on `Dispatchers.IO`; writes dispatch to
  `Dispatchers.IO` for blocking socket operations.
- Permissions: Requires `BLUETOOTH_CONNECT` at runtime.

### JVM (stub)

| File | Role |
|------|------|
| `L2capListenerFactory.jvm.kt` | Throws `L2capException.NotSupported` |

No Bluetooth stack on JVM host -- all L2CAP operations throw `NotSupported`.

## Platform Parity

| Feature | iOS | Android | JVM |
|---------|-----|---------|-----|
| Client channel open | Yes (CBL2CAPChannel) | Yes (createL2capChannel) | No |
| Server listener | Yes (publishL2CAPChannel) | Yes (listenUsingL2capChannel) | No |
| Secure channels | Connection-level (flag ignored) | Per-channel (createL2capChannel) | -- |
| MTU discovery | Configurable default (2048) | Socket query (>=672) | -- |
| Write/read streaming | NSStream → Channel→Flow | InputStream/OutputStream → Channel→Flow | -- |
| Disconnect cleanup | Automatic (onDisconnectCleanup) | Automatic (onDisconnectCleanup) | -- |
| Test fakes | FakeL2capChannel, FakeL2capListener (commonMain) | Same fakes | NotSupported |

## Error Handling

`L2capException` is a sealed hierarchy providing typed errors for each failure
mode. Platform implementations map OS-level errors to the appropriate subtype:

| Exception Type | When Thrown |
|----------------|-------------|
| `OpenFailed(psm, message, cause)` | Platform channel open fails |
| `WriteFailed(message, cause)` | Output stream write fails |
| `ChannelClosed(message)` | Write on closed channel |
| `NotConnected(message)` | Channel operation without connection |
| `NotSupported(message)` | L2CAP unavailable (JVM, old OS) |
| `PublishFailed(message, cause)` | Listener publish rejected/timed out |
| `InvalidState(message)` | Wrong state for operation (e.g., open twice) |

## Testing

**commonTest fakes:**
- `FakeL2capChannel`: Configurable PSM/MTU, records writes, simulates
  incoming data via configurable flow.
- `FakeL2capListener`: Records open/close, emits `FakeL2capChannel`
  instances on `incoming`.

**Unit tests (commonTest):**
- `L2capChannelTest.kt` (288 loc): Channel lifecycle, write, read, incoming
  flow, close behavior.
- `FakeL2capListenerTest.kt` (102 loc): Listener lifecycle, PSM assignment,
  channel acceptance, close behavior.

**Platform tests:**
- `AndroidL2capChannelTest.kt` (308 loc): Platform-specific tests requiring
  Android runtime.
- `FakeL2capSocket.kt` (77 loc): Test helper for socket mocking on Android.

**Gap:** No L2CAP conformance tests in `BleConformanceTest` (filed as #249).

## Concurrency Model

- **Structured concurrency:** No GlobalScope usage. Channels own their
  coroutine scope (constructor-injected).
- **Platform thread safety:** Read/write loops use `limitedParallelism(1)`
  dispatchers or dedicated platform queues (GCD dispatch queue on iOS).
- **Cancellation:** Channel close cancels the read loop via Job cancellation.
  Platform-specific cleanup (socket close, stream close) happens in finally blocks.
- **Backpressure:** `Channel<ByteArray>(BUFFERED)` provides bounded buffering.
  Slow consumers cause the read loop to suspend, which stops draining OS
  buffers and triggers L2CAP flow control on the remote device.

## File Inventory

| Location | Files | Total Lines |
|----------|-------|-------------|
| commonMain/l2cap | 3 | 290 |
| commonMain/testing (fakes) | 3 (+2 shared) | ~350 |
| iosMain/l2cap | 2 | 433 |
| iosMain/peripheral L2CAP | 1 | 83 |
| androidMain/l2cap | 4 | 326 |
| androidMain/peripheral L2CAP | 1 | 157 |
| jvmMain | 1 | ~5 |
| Tests (commonTest) | 2 | 390 |
| Tests (androidHostTest) | 2 | 385 |
| **Total** | **~20 files** | **~2,230** |
