# BLE Mesh Architecture for kmp-ble

> **Status:** Architecture Design | **Author:** Gary Quinn | **Date:** 2026-07-22
>
> Architecture document for the `kmp-ble-mesh` module -- Bluetooth Mesh networking on Android, iOS, and JVM.

---

## Table of Contents

1. [System Context](#1-system-context)
2. [Protocol Stack Deep Dive](#2-protocol-stack-deep-dive)
3. [Architecture Overview](#3-architecture-overview)
4. [Module Structure](#4-module-structure)
5. [Core Interfaces & Class Diagram](#5-core-interfaces--class-diagram)
6. [Provisioning Architecture](#6-provisioning-architecture)
7. [Crypto Architecture](#7-crypto-architecture)
8. [Proxy Transport Architecture](#8-proxy-transport-architecture)
9. [Message Flow Architecture](#9-message-flow-architecture)
10. [Concurrency & Threading Model](#10-concurrency--threading-model)
11. [Sequence Number & IV Index Management](#11-sequence-number--iv-index-management)
12. [Persistence Architecture](#12-persistence-architecture)
13. [Model Dispatch Architecture](#13-model-dispatch-architecture)
14. [Standard Models](#14-standard-models)
15. [Error Architecture](#15-error-architecture)
16. [Testing Architecture](#16-testing-architecture)
17. [Phased Implementation Plan](#17-phased-implementation-plan)
18. [Key Design Decisions & Trade-offs](#18-key-design-decisions--trade-offs)
19. [Risk Assessment](#19-risk-assessment)
20. [Build Configuration](#20-build-configuration)
21. [Usage Example](#21-usage-example)
22. [References](#22-references)

---

## 1. System Context

### 1.1 The Smartphone's Role in BLE Mesh

```mermaid
C4Context
    title System Context: Smartphone as BLE Mesh Participant

    Person(user, "User", "Controls mesh devices via app")

    System_Boundary(phone, "Smartphone") {
        System(mesh_app, "App", "Uses kmp-ble-mesh API")
        System(kmp_ble_mesh, "kmp-ble-mesh", "Mesh protocol stack in software")
        System(kmp_ble_core, "kmp-ble Core", "BLE scanning & GATT operations")
    }

    System_Boundary(mesh_network, "BLE Mesh Network") {
        System(proxy_node, "Proxy Node", "Bridges GATT ↔ ADV bearer\nHas relay capability\nAlways mains-powered")
        System(relay_node, "Relay Nodes", "Extend mesh range\nForward PDUs via ADV bearer")
        System(end_node, "End Nodes", "Lights, sensors, switches\nMay be LPN (Low Power Node)")
        System(friend_node, "Friend Node", "Caches messages for LPN\nDelivers on poll")
    }

    Rel(user, mesh_app, "Uses")
    Rel(mesh_app, kmp_ble_mesh, "Calls")
    Rel(kmp_ble_mesh, kmp_ble_core, "Uses")
    Rel(kmp_ble_core, proxy_node, "GATT connection\n(Proxy Protocol)", "BLE")
    Rel(proxy_node, relay_node, "ADV Bearer\n(flood mesh)", "BLE")
    Rel(relay_node, end_node, "ADV Bearer", "BLE")
    Rel(friend_node, end_node, "Friendship\n(cached delivery)", "BLE")
```

**Fundamental architectural constraint:** The smartphone is a **second-class mesh citizen**. It cannot transmit on the ADV bearer (mobile OSes don't expose raw advertising transmission for mesh). All communication goes through a single GATT Proxy connection. This means:

- **No relaying** - the phone never forwards mesh messages
- **No Friend/LPN role** - phone is either connected (via proxy) or offline
- **Single point of failure** - if the proxy node goes down, the phone loses mesh connectivity
- **Asymmetric bandwidth** - receive is notification-driven (moderate), send is GATT write (limited by connection interval)

### 1.2 Network Topology

```mermaid
graph TB
    subgraph "BLE Mesh Network"
        PN[Proxy Node<br/>addr: 0x0002<br/>relay+proxy enabled]
        RN1[Relay Node<br/>addr: 0x0003]
        RN2[Relay Node<br/>addr: 0x0004]
        L1[Light<br/>addr: 0x0005<br/>OnOff Server]
        L2[Light<br/>addr: 0x0006<br/>OnOff Server]
        S1[Sensor<br/>addr: 0x0007<br/>Sensor Server]
        FN[Friend Node<br/>addr: 0x0008]
        LPN[LPN Sensor<br/>addr: 0x0009]
    end

    subgraph "Smartphone"
        APP[kmp-ble-mesh App<br/>unicast: 0x0001]
    end

    APP <==>|GATT Proxy<br/>single connection| PN
    PN -->|ADV Bearer| RN1
    PN -->|ADV Bearer| RN2
    RN1 --> L1
    RN1 --> L2
    RN2 --> S1
    RN2 --> FN
    FN -.->|Friendship<br/>cached delivery| LPN
```

**Address allocation example:**

| Device | Unicast Address | Elements | Models |
|--------|----------------|----------|--------|
| Smartphone | 0x0001 | 1 element | Config Client, Health Client, Generic OnOff Client |
| Proxy Node | 0x0002-0x0004 | 3 elements | Config Server, Health Server, OnOff Server (element 2,3) |
| Relay Node 1 | 0x0005-0x0006 | 2 elements | Config Server, Health Server |
| Light 1 | 0x0007 | 1 element | OnOff Server, Level Server |
| Sensor 1 | 0x0008 | 1 element | Sensor Server |

---

## 2. Protocol Stack Deep Dive

### 2.1 Full Protocol Stack

```mermaid
graph TB
    subgraph "BLE Mesh Protocol Stack (implemented in software)"
        direction TB
        ML["<b>Model Layer</b><br/>Generic OnOff, Level, Sensor, Vendor<br/>Client-Server pattern<br/>Publish-Subscribe addressing"]
        FL["<b>Foundation Model Layer</b><br/>Configuration Server/Client (mandatory)<br/>Health Server/Client (mandatory)"]
        AL["<b>Access Layer</b><br/>Opcode formatting (1/2/3 byte)<br/>Application payload structure<br/>Model dispatch by opcode+address"]
        UTL["<b>Upper Transport Layer</b><br/>AppKey encryption (AES-128-CCM, 32/64-bit MIC)<br/>Transport control messages<br/>Friend/LPN session management"]
        LTL["<b>Lower Transport Layer</b><br/>Segmentation & Reassembly (SAR)<br/>Max 32 segments × 12 bytes = 384 bytes<br/>Block ACK for reliable delivery"]
        NL["<b>Network Layer</b><br/>NetKey encryption (AES-128-CCM)<br/>Addressing: Unicast/Group/Virtual<br/>Privacy: AES-ECB obfuscation<br/>TTL + message cache + replay protection"]
        BL["<b>Bearer Layer</b><br/>ADV Bearer: broadcast, connectionless<br/>GATT Bearer: connection-oriented proxy"]
    end

    subgraph "Platform BLE Stack"
        PL["Android: BluetoothGatt<br/>iOS: CoreBluetooth<br/>JVM: Not supported"]
    end

    BL --> PL
    ML --> FL --> AL --> UTL --> LTL --> NL --> BL
```

### 2.2 Network PDU Structure (29 bytes max)

```mermaid
packet-beta
    title Network PDU Format (unsegmented access message)
    0-0: "IVI(1b)"
    0-6: "NID(7b)"
    7-7: "CTL(1b)"
    7-13: "TTL(7b)"
    14-37: "SEQ(24b)"
    38-53: "SRC(16b)"
    54-69: "DST(16b)"
    70-197: "TransportPDU(1-16 bytes)"
    198-229: "NetMIC(32b)"
```

### 2.3 Bearer Layer - The Asymmetric Reality

This is the most architecturally significant layer because it's where the smartphone's constraints manifest.

```mermaid
graph LR
    subgraph "Bearer Layer: Two Fundamentally Different Transports"
        subgraph "ADV Bearer"
            ADV_TX["TX: BLE Advertising<br/>(ADV_NONCONN_IND)<br/>Phone CANNOT use this"]
            ADV_RX["RX: BLE Scanning<br/>Phone CAN use this<br/>(for discovery only)"]
        end

        subgraph "GATT Bearer (Proxy)"
            GATT_TX["TX: GATT Write<br/>to Proxy Data In char<br/>Phone MUST use this"]
            GATT_RX["RX: GATT Notify<br/>from Proxy Data Out char<br/>Phone MUST use this"]
        end
    end

    ADV_RX -->|"PB-ADV discovery<br/>of unprovisioned devices"| GATT_TX
    GATT_RX -->|"Incoming mesh PDUs<br/>from proxy node"| GATT_TX
```

| Bearer | Phone Can Send? | Phone Can Receive? | Used For |
|--------|:---:|:---:|---|
| **ADV Bearer** | ❌ (OS restriction) | ✅ (via Scanner) | Discovering unprovisioned device beacons |
| **GATT Proxy Bearer** | ✅ (via Peripheral.write) | ✅ (via Peripheral.observe) | **All** smartphone mesh communication |

**Architectural implication:** We only need to implement GATT Proxy transmission. The ADV bearer is receive-only for phone, and only used during provisioning discovery. This simplifies the TX side but means the bearer abstraction must be asymmetric.

---

## 3. Architecture Overview

### 3.1 Component Architecture

```mermaid
graph TB
    subgraph "Public API Layer"
        MN[MeshNetwork<br/>AutoCloseable]
        MP[MeshProvisioner<br/>AutoCloseable]
        CC[ConfigurationClient]
        GO[GenericOnOffClient]
        GL[GenericLevelClient]
        SS[SensorClient]
        PC[ProxyConnection<br/>AutoCloseable]
    end

    subgraph "commonMain Implementation"
        direction TB
        subgraph "Protocol Stack"
            PROV[Provisioning<br/>State Machine]
            NET[Network Layer<br/>encrypt/decrypt/relay]
            TRANS[Transport Layer<br/>SAR + app encrypt]
            ACCESS[Access Layer<br/>opcode routing]
        end

        subgraph "Infrastructure"
            CRYPTO[Crypto Engine<br/>expect/actual]
            SEQ[Sequence Number<br/>Manager]
            IVI[IV Index<br/>Tracker]
            DISP[Model Dispatcher<br/>opcode → handler]
            STORE[MeshStateStore<br/>persistence SPI]
            SAR_PROXY[Proxy SAR<br/>GATT MTU aware]
            SAR_MESH[Mesh SAR<br/>32-segment max]
        end
    end

    subgraph "Platform Bridge (expect/actual)"
        direction LR
        ANDROID[Android<br/>javax.crypto]
        IOS[iOS<br/>CommonCrypto/CryptoKit]
        JVM[JVM<br/>NotSupported]
    end

    subgraph "Core kmp-ble APIs"
        PERIPH[Peripheral]
        SCANNER[Scanner]
    end

    MN --> PROV
    MN --> NET
    MN --> PC
    MP --> PROV
    PC --> PERIPH
    PROV --> SCANNER
    PROV --> CRYPTO
    NET --> CRYPTO
    NET --> SEQ
    NET --> IVI
    TRANS --> CRYPTO
    ACCESS --> DISP
    DISP --> GO
    DISP --> GL
    DISP --> SS
    CRYPTO --> ANDROID
    CRYPTO --> IOS
    CRYPTO --> JVM
    STORE --> MN
```

### 3.2 Layer Dependencies (Strict Layering)

```mermaid
graph TD
    Model[Model Layer] --> Foundation[Foundation Model Layer]
    Foundation --> Access[Access Layer]
    Access --> UpperTransport[Upper Transport Layer]
    UpperTransport --> LowerTransport[Lower Transport Layer]
    LowerTransport --> Network[Network Layer]
    Network --> Bearer[Bearer Layer]
    Bearer --> Platform[Platform BLE APIs]

    Crypto[Crypto Engine] -.-> Network
    Crypto -.-> UpperTransport
    Crypto -.-> Access
    SeqMgr[Sequence Number Mgr] -.-> Network
    IvIdx[IV Index Tracker] -.-> Network
    StateStore[MeshStateStore] -.-> SeqMgr
    StateStore -.-> IvIdx
```

### 3.3 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **API levels** | Raw PDU + High-level model API | Power users need raw access for custom/vendor models; typical users want typed model APIs. Matches existing pattern: `Peripheral.read/write` + `peripheral.heartRateMeasurements()` |
| **Crypto strategy** | Pure Kotlin AES-128-CCM + platform ECDH | AES-128-CCM is a well-defined algorithm with spec test vectors -- a pure Kotlin implementation is portable, auditable, and avoids iOS CCM API gaps. ECDH P-256 uses platform hardware-backed keystores for security. |
| **Crypto fallback** | Pure Kotlin fallback for ALL primitives | If platform crypto fails (rare but possible on some Android OEMs), fallback to pure Kotlin ensures mesh still works. The pure Kotlin impl is production-quality, not just a test stub. |
| **Persistence** | SPI `MeshStateStore` with `InMemory` default | Consumers own their storage backend (DataStore, Keychain, file). Sequence numbers MUST survive crashes -- making this a consumer responsibility with clear documentation is safer than a hidden default. |
| **Concurrency** | `limitedParallelism(1)` dispatcher per proxy + `Mutex` on shared network state | Proven pattern from core library. Per-proxy serialization prevents GATT queue corruption. Mutex on shared state prevents race between incoming message handler and user API calls. |
| **Bearer abstraction** | Asymmetric: read from both ADV+GATT, write only via GATT | Reflects platform reality. Phone cannot TX on ADV bearer. Making this explicit in the type system prevents impossible operations. |
| **Proxy redundancy** | Single proxy with reconnect; multi-proxy in phase 4 | 95% of mesh deployments have one proxy node in phone range. Multi-proxy adds significant complexity (two IV Index sources, duplicate PDU filtering). Design for it but don't build it yet. |
| **Model codegen** | Manual first, codegen later | Early-stage API needs iteration. Once the patterns stabilize for 3+ models, a code generator from SIG XML model definitions becomes high-ROI. |

---

## 4. Module Structure

```
kmp-ble-mesh/
├── build.gradle.kts
├── MODULE.md
└── src/
    ├── commonMain/kotlin/com/atruedev/kmpble/mesh/
    │   ├── MeshNetwork.kt              # Core network interface
    │   ├── MeshNode.kt                 # Provisioned node
    │   ├── MeshElement.kt              # Addressable element
    │   ├── MeshModel.kt                # Model identifier + opcode types
    │   ├── MeshAddress.kt              # UnicastAddress, GroupAddress, VirtualAddress
    │   ├── MeshKey.kt                  # NetworkKey, ApplicationKey, DeviceKey
    │   ├── MeshPdu.kt                  # All PDU type definitions
    │   ├── MeshError.kt                # Error hierarchy (sealed interfaces)
    │   ├── MeshStateStore.kt           # Persistence SPI
    │   ├── ExperimentalMeshApi.kt      # Opt-in annotation for unstable APIs
    │   │
    │   ├── provisioning/
    │   │   ├── MeshProvisioner.kt      # Public provisioner interface
    │   │   ├── ProvisioningBearer.kt   # PB-ADV + PB-GATT bearer abstraction
    │   │   ├── ProvisioningStateMachine.kt  # 5-phase state machine
    │   │   ├── ProvisioningCapabilities.kt  # Device capabilities model
    │   │   ├── ProvisioningData.kt     # Distributed provisioning data
    │   │   └── OobAuthentication.kt    # OOB auth methods
    │   │
    │   ├── network/
    │   │   ├── NetworkLayer.kt         # PDU encode/decode, relay, privacy
    │   │   ├── LowerTransportLayer.kt  # Segmentation & reassembly (mesh SAR)
    │   │   ├── UpperTransportLayer.kt  # AppKey encrypt/decrypt, control msgs
    │   │   ├── AccessLayer.kt          # Opcode format, model dispatch
    │   │   ├── NetworkPduBuilder.kt    # Immutable PDU construction
    │   │   └── MessageCache.kt         # Duplicate detection cache
    │   │
    │   ├── proxy/
    │   │   ├── ProxyClient.kt          # Public proxy connection interface
    │   │   ├── ProxyProtocol.kt        # Proxy PDU format + SAR (GATT-level)
    │   │   ├── ProxyFilter.kt          # Filter types
    │   │   └── MeshProxyService.kt     # UUIDs, GATT characteristic discovery
    │   │
    │   ├── config/
    │   │   ├── ConfigurationClient.kt  # Post-provision config operations
    │   │   └── ConfigMessages.kt       # Config opcode definitions
    │   │
    │   ├── models/
    │   │   ├── ModelDispatcher.kt      # Opcode → handler routing
    │   │   ├── generic/
    │   │   │   ├── GenericOnOffClient.kt
    │   │   │   ├── GenericOnOffServer.kt
    │   │   │   ├── GenericLevelClient.kt
    │   │   │   └── GenericLevelServer.kt
    │   │   ├── sensor/
    │   │   │   ├── SensorClient.kt
    │   │   │   └── SensorServer.kt
    │   │   └── VendorModel.kt           # Custom vendor model registration
    │   │
    │   ├── crypto/
    │   │   ├── CryptoEngine.kt          # expect object: all primitives
    │   │   ├── AesCcm.kt                # Pure Kotlin AES-128-CCM
    │   │   ├── AesCmac.kt               # Pure Kotlin AES-128-CMAC
    │   │   ├── KeyDerivation.kt         # k1/k2/k3/s1 functions
    │   │   └── NonceGenerator.kt        # Per-layer nonce construction
    │   │
    │   ├── internal/
    │   │   ├── MeshNetworkImpl.kt       # Network implementation
    │   │   ├── SequenceNumberManager.kt # 24-bit seq + RPL tracking
    │   │   ├── IvIndexTracker.kt        # IV Index + update procedure
    │   │   ├── SegmentedMessageAssembler.kt  # Inbound reassembly buffers
    │   │   ├── InMemoryMeshStateStore.kt     # Default storage impl
    │   │   └── MeshLogger.kt            # Structured logging
    │   │
    │   └── testing/
    │       ├── FakeMeshNetwork.kt       # Test double
    │       ├── FakeMeshNode.kt          # Test double
    │       ├── FakeProvisioner.kt       # Test double (simulates device)
    │       ├── FakeProxyClient.kt       # Test double (PDU channel)
    │       └── FakeMeshStateStore.kt    # Test double
    │
    ├── commonTest/kotlin/com/atruedev/kmpble/mesh/
    │   ├── crypto/
    │   │   ├── AesCcmTest.kt            # SIG spec test vectors
    │   │   ├── CmacTest.kt              # RFC 4493 test vectors
    │   │   └── KeyDerivationTest.kt     # k1/k2/k3 test vectors
    │   ├── network/
    │   │   ├── NetworkLayerTest.kt
    │   │   └── TransportLayerTest.kt
    │   ├── provisioning/
    │   │   ├── ProvisioningStateMachineTest.kt
    │   │   └── ProvisioningConformanceTest.kt
    │   ├── config/
    │   │   └── ConfigurationClientTest.kt
    │   ├── models/
    │   │   └── GenericOnOffTest.kt
    │   └── MeshNetworkConformanceTest.kt
    │
    ├── androidMain/kotlin/com/atruedev/kmpble/mesh/
    │   └── crypto/CryptoEngine.android.kt  # javax.crypto impl
    │
    └── iosMain/kotlin/com/atruedev/kmpble/mesh/
        └── crypto/CryptoEngine.ios.kt      # CommonCrypto/CryptoKit impl
```

---

## 5. Core Interfaces & Class Diagram

### 5.1 Core Type Hierarchy

```mermaid
classDiagram
    class MeshAddress {
        <<sealed interface>>
        +value: UShort
    }
    class UnicastAddress {
        +value: UShort
        range: 0x0001..0x7FFF
    }
    class GroupAddress {
        +value: UShort
        range: 0xC000..0xFFFF
    }
    class VirtualAddress {
        +labelUuid: Uuid
        +value: UShort
    }

    class MeshKey {
        <<sealed interface>>
        +index: UShort
        +key: ByteArray
    }
    class NetworkKey {
        +index: UShort
        +key: ByteArray
        +phase: KeyRefreshPhase
    }
    class ApplicationKey {
        +index: UShort
        +key: ByteArray
        +boundNetKeyIndex: UShort
    }
    class DeviceKey {
        +key: ByteArray
    }

    class MeshNetwork {
        <<interface>>
        +nodes: StateFlow~List~MeshNode~~
        +ivIndex: StateFlow~IvIndex~
        +networkKeys: List~NetworkKey~
        +applicationKeys: List~ApplicationKey~
        +connectProxy(peripheral): ProxyConnection
        +send(dst, model, opcode, data, appKey): MeshMessageResponse?
        +incomingMessages: Flow~MeshMessage~
        +configurationClient: ConfigurationClient
        +close()
    }
    class MeshNetworkBuilder {
        +networkKey(key)
        +applicationKey(key)
        +element(element)
        +stateStore(store)
    }

    MeshAddress <|-- UnicastAddress
    MeshAddress <|-- GroupAddress
    MeshAddress <|-- VirtualAddress
    MeshKey <|-- NetworkKey
    MeshKey <|-- ApplicationKey
    MeshKey <|-- DeviceKey
    MeshNetwork ..> MeshNode
    MeshNetwork ..> MeshAddress
    MeshNetwork ..> NetworkKey
    MeshNetwork ..> ApplicationKey
    MeshNetworkBuilder --> MeshNetwork : builds
```

### 5.2 MeshNetwork - The Central Interface

```kotlin
public interface MeshNetwork : AutoCloseable {
    // --- Identity ---
    public val ownUnicastAddress: UnicastAddress

    // --- Observable State ---
    public val nodes: StateFlow<List<MeshNode>>
    public val ivIndex: StateFlow<IvIndex>
    public val isProxyConnected: StateFlow<Boolean>

    // --- Key Management ---
    public val networkKeys: List<NetworkKey>
    public val applicationKeys: List<ApplicationKey>
    public suspend fun addNetworkKey(key: NetworkKey)
    public suspend fun addApplicationKey(key: ApplicationKey)

    // --- Node Management ---
    public suspend fun addNode(node: MeshNode)
    public suspend fun removeNode(address: UnicastAddress)
    public fun findNode(address: UnicastAddress): MeshNode?

    // --- Connectivity ---
    public suspend fun connectProxy(peripheral: Peripheral): ProxyConnection
    public suspend fun disconnectProxy()

    // --- Messaging ---
    public suspend fun send(
        destination: MeshAddress,
        modelId: MeshModelId,
        opcode: MeshOpcode,
        payload: ByteArray,
        appKey: ApplicationKey,
        acknowledged: Boolean = true,
        ttl: UByte = DEFAULT_TTL,
    ): MeshMessageResponse?

    public val incomingMessages: Flow<MeshMessage>

    // --- Configuration ---
    public val configurationClient: ConfigurationClient

    // --- Lifecycle ---
    override fun close()
}
```

### 5.3 MeshNode & MeshElement

```kotlin
public data class MeshNode(
    val unicastAddress: UnicastAddress,
    val deviceKey: DeviceKey,
    val elements: List<MeshElement>,
    val features: NodeFeatures,
    val ttl: UByte = DEFAULT_TTL,
)

public data class NodeFeatures(
    val relay: Boolean = false,
    val proxy: Boolean = false,
    val friend: Boolean = false,
    val lowPower: Boolean = false,
)

public data class MeshElement(
    val index: Int,                    // 0-based within node
    val unicastAddress: UnicastAddress, // nodeAddress + index
    val location: ElementLocation,
    val models: List<MeshModelId>,
)

public value class MeshModelId(val id: UInt) {
    val isSigModel: Boolean get() = id < 0xFFFFu
    val sigId: UShort get() = id.toUShort()
    val vendorId: UShort get() = ((id shr 16) and 0xFFFFu).toUShort()
}
```

### 5.4 Entity Relationship

```mermaid
erDiagram
    MeshNetwork ||--o{ MeshNode : "has nodes"
    MeshNetwork ||--o{ NetworkKey : "has net keys"
    MeshNetwork ||--o{ ApplicationKey : "has app keys"
    MeshNode ||--o{ MeshElement : "has elements"
    MeshElement ||--o{ MeshModel : "has models"
    ApplicationKey }o--|| NetworkKey : "bound to"
    MeshNode ||--|| DeviceKey : "has device key"
    MeshNetwork ||--|| IvIndex : "has"
    MeshNetwork ||--|| MeshStateStore : "persists via"
    MeshNetwork ||--o| ProxyConnection : "connects via"
    ProxyConnection ||--|| Peripheral : "uses"
```

---

## 6. Provisioning Architecture

### 6.1 Provisioning Protocol State Machine

```mermaid
stateDiagram-v2
    [*] --> Discovery: scanForUnprovisionedDevices()
    Discovery --> CapabilitiesExchange: device found, bearer opened
    CapabilitiesExchange --> AlgorithmSelection: capabilities received
    AlgorithmSelection --> PublicKeyExchange: OOB method selected
    PublicKeyExchange --> Authentication: ECDH complete
    Authentication --> DataDistribution: confirmation verified
    DataDistribution --> Complete: provisioning data sent
    Complete --> [*]: MeshNode created

    Discovery --> Failed: timeout / no device
    CapabilitiesExchange --> Failed: bearer error
    PublicKeyExchange --> Failed: key exchange error
    Authentication --> Failed: OOB mismatch
    DataDistribution --> Failed: encryption error

    Failed --> [*]: ProvisioningFailed error
```

### 6.2 Provisioning Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant App
    participant Provisioner as MeshProvisioner
    participant Bearer as ProvisioningBearer
    participant Crypto as CryptoEngine
    participant Device as Unprovisioned Device

    User->>App: Tap "Add Device"
    App->>Provisioner: scanForUnprovisionedDevices()

    loop Scanning
        Device-->>Bearer: Unprovisioned Device Beacon (UUID, OOB info)
        Bearer-->>Provisioner: scanEvents.emit(device)
    end

    App->>Provisioner: provision(device, netKey, address, oob)
    Provisioner->>Bearer: open(device)

    Note over Provisioner,Device: Phase 1: Invitation
    Provisioner->>Bearer: ProvisioningInvite(attentionDuration=5s)

    Note over Provisioner,Device: Phase 2: Capabilities
    Device-->>Bearer: ProvisioningCapabilities(elements=3, algo=FIPS_P256, staticOOB=true)
    Bearer-->>Provisioner: capabilities

    Note over Provisioner,Device: Phase 3: Start
    Provisioner->>Bearer: ProvisioningStart(algo=FIPS_P256, oob=StaticOOB)

    Note over Provisioner,Device: Phase 4: Public Key Exchange
    Provisioner->>Crypto: ecdhP256GenerateKeyPair()
    Crypto-->>Provisioner: (publicKey, privateKey)
    Provisioner->>Bearer: ProvisioningPublicKey(provPublicKey)
    Device-->>Bearer: ProvisioningPublicKey(devicePublicKey)
    Provisioner->>Crypto: ecdhP256SharedSecret(myPrivate, devicePublic)
    Crypto-->>Provisioner: sharedSecret (ECDHSecret)

    Note over Provisioner,Device: Phase 5: Authentication (Static OOB)
    Provisioner->>Crypto: secureRandomBytes(16)
    Crypto-->>Provisioner: randomProv
    Provisioner->>Crypto: aesCmac(confirmationKey, randomProv || oobKey)
    Crypto-->>Provisioner: confirmationProv
    Provisioner->>Bearer: ProvisioningConfirmation(confirmationProv)
    Device-->>Bearer: ProvisioningConfirmation(confirmationDevice)
    Provisioner->>Bearer: ProvisioningRandom(randomProv)
    Device-->>Bearer: ProvisioningRandom(randomDevice)
    Note over Provisioner: Verify device confirmation
    Provisioner->>Provisioner: check: CMAC(key, randomDevice || oob) == receivedConfirmation

    Note over Provisioner,Device: Phase 6: Data Distribution
    Provisioner->>Crypto: k1(sharedSecret, confirmationSalt, "prsk")
    Crypto-->>Provisioner: sessionKey
    Provisioner->>Crypto: aesCcmEncrypt(sessionKey, sessionNonce, provisioningData)
    Crypto-->>Provisioner: encryptedData + MIC
    Provisioner->>Bearer: ProvisioningData(encrypted(NetKey, KeyIdx, IVI, UnicastAddr, DevKey))
    Device-->>Bearer: ProvisioningComplete()

    Provisioner-->>App: MeshNode provisioned
```

### 6.3 OOB Authentication Methods

```mermaid
graph TD
    OOB[OobAuthentication] --> None[None<br/>AuthValue = 0x0000...]
    OOB --> Static[StaticOob<br/>AuthValue = 16-byte key<br/>e.g. printed on device]
    OOB --> Output[OutputOob<br/>Device outputs number<br/>User enters on phone<br/>Actions: blink, beep, vibrate, display]
    OOB --> Input[InputOob<br/>Phone displays number<br/>User enters on device<br/>Actions: push, twist, numeric, alphanumeric]

    None -->|"No user interaction<br/>Vulnerable to MITM"| Auth[Authentication]
    Static -->|"Pre-shared key<br/>Moderate security"| Auth
    Output -->|"User verifies<br/>Good security"| Auth
    Input -->|"User enters<br/>Best security"| Auth
```

---

## 7. Crypto Architecture

### 7.1 Key Hierarchy

```mermaid
graph TD
    subgraph "Key Hierarchy (from Mesh Spec)"
        NetKey[NetworkKey<br/>Shared by all nodes in subnet<br/>Encrypts Network Layer]
        AppKey[ApplicationKey<br/>Bound to one NetKey<br/>Encrypts Access Layer]
        DevKey[DeviceKey<br/>Unique per node<br/>Encrypts Config messages]

        subgraph "Derived Keys (computed, never stored)"
            EncKey[Encryption Key<br/>K2(NetKey, 0x00)]
            PrivKey[Privacy Key<br/>K2(NetKey, 0x01)]
            NID[Network ID<br/>K2(NetKey, 0x02)<br/>7-bit, in PDU header]
            IdentityKey[Identity Key<br/>K1(NetKey, IdentitySalt, DevKey)<br/>For proxy advertising]
            BeaconKey[Beacon Key<br/>K1(NetKey, BeaconSalt, 0x00)<br/>For secure network beacons]
            SessionKey[Session Key<br/>K1(ECDHSecret, ConfirmationSalt, "prsk")<br/>For provisioning data encryption]
            ConfirmationKey[Confirmation Key<br/>K1(ECDHSecret, ConfirmationSalt, "prck")]
        end
    end

    NetKey --> EncKey
    NetKey --> PrivKey
    NetKey --> NID
    NetKey --> IdentityKey
    NetKey --> BeaconKey
    DevKey --> IdentityKey
    AppKey -.->|bound to| NetKey
```

### 7.2 Crypto Engine Design

```mermaid
graph TB
    subgraph "commonMain: Internal API"
        CE[CryptoEngine<br/>expect object]
        AesCcm[AesCcm.kt<br/>Pure Kotlin CTR+CBC-MAC]
        AesCmac[AesCmac.kt<br/>Pure Kotlin CMAC]
        KeyDer[KeyDerivation.kt<br/>k1/k2/k3/s1<br/>Uses AesCmac]
        NonceGen[NonceGenerator.kt<br/>Per-layer nonce format]
    end

    subgraph "Platform actuals"
        Android[Android<br/>javax.crypto.Cipher<br/>javax.crypto.KeyAgreement<br/>java.security.MessageDigest]
        IOS[iOS<br/>CommonCrypto / CryptoKit<br/>SecKeyCopyKeyExchangeResult]
        JVM[JVM<br/>javax.crypto<br/>throws NotSupported]
    end

    subgraph "Fallback (all platforms)"
        Fallback[PureKotlinCrypto<br/>Production-quality<br/>AES via pre-computed S-box<br/>ECDH via BouncyCastle-like impl]
    end

    CE --> AesCcm
    CE --> AesCmac
    CE --> Android
    CE --> IOS
    CE --> JVM
    CE -.->|"if platform fails"| Fallback
    AesCmac --> KeyDer
```

**Why pure Kotlin AES-128-CCM?** iOS CommonCrypto does not expose CCM mode directly (it has CTR and CBC-MAC separately, but combining them correctly for CCM with variable MIC sizes is non-trivial). Rather than platform-specific CCM implementations that diverge in edge cases, a single pure Kotlin implementation with SIG spec test vectors gives us determinism and portability. ECDH and SHA-256 are different: they're simple enough that platform APIs are safe and provide hardware-backed key storage.

### 7.3 Nonce Construction

Each protocol layer constructs its own nonce to prevent cross-layer nonce reuse:

| Layer | Nonce Bytes | Format |
|-------|------------|--------|
| Network | 13 bytes | `[NonceType(1)] [IVI+SEQ(4)] [SRC(2)] [DST(2)] [IVI(4)]` |
| Upper Transport (App) | 13 bytes | `[NonceType(1)] [SRC(2)] [DST(2)] [SEQ(3)] [SZMIC(1)] [IVI(4)]` |
| Proxy | 13 bytes | `[NonceType(1)] [SeqNum(4)] [SRC(2)] [DST(2)] [IVI(4)]` |
| Provisioning | 13 bytes | `[NonceType(1)] [SeqNum(4)] [SRC(2)] [DST(2)] [IVI(4)]` |

The `NonceType` byte ensures nonces from different layers can never collide even if all other fields are identical.

---

## 8. Proxy Transport Architecture

### 8.1 Proxy Connection Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Disconnected
    Disconnected --> Connecting: connectProxy(peripheral)
    Connecting --> ServiceDiscovery: GATT connected
    ServiceDiscovery --> FilterSetup: Mesh Proxy Service found
    FilterSetup --> Connected: filter set, notifications enabled
    Connected --> Disconnecting: disconnectProxy() or peripheral disconnect
    Connected --> Reconnecting: peripheral disconnect (auto)
    Reconnecting --> ServiceDiscovery: reconnection handler fires
    Disconnecting --> Disconnected: GATT disconnected
    Connected --> [*]: close()
    Disconnected --> [*]: close()
```

### 8.2 Proxy PDU SAR (Double SAR Architecture)

```mermaid
sequenceDiagram
    participant Mesh as Mesh Network
    participant Proxy as ProxyClient
    participant GATT as Peripheral (GATT)
    participant Node as Proxy Node

    Note over Mesh,Node: Sending a large mesh message (> MTU-2 bytes)

    Mesh->>Mesh: Access Payload (200 bytes)<br/>↓ Lower Transport SAR<br/>Segments into 17 segments × 12 bytes
    Mesh->>Mesh: Each segment → Network PDU (29 bytes)<br/>17 Network PDUs total
    Mesh->>Proxy: sendPdu(pdu1), sendPdu(pdu2), ...

    Note over Proxy,Node: Proxy SAR: chunking Network PDUs over GATT MTU

    Proxy->>Proxy: Network PDU (29 bytes) > GATT MTU-3<br/>Split into SAR segments
    Proxy->>GATT: write(Proxy Data In, SAR=First, data[0..17])
    Proxy->>GATT: write(Proxy Data In, SAR=Last, data[18..28])
    GATT->>Node: GATT write

    Note over Proxy,Node: Receiving: GATT notification → Proxy SAR reassembly → Network PDU

    Node-->>GATT: notify(Proxy Data Out, SAR=Complete, networkPDU)
    GATT-->>Proxy: incoming notification
    Proxy->>Proxy: Reassemble if SAR segmented
    Proxy-->>Mesh: incomingPdu(networkPdu)
    Mesh->>Mesh: Network decrypt → Transport reassembly → Access decrypt → Model dispatch
```

**Critical architectural distinction:** There are TWO independent SAR layers:
1. **Proxy SAR** (GATT-level): Splits Network PDUs across GATT writes/notifications when PDU > (MTU-3)
2. **Mesh Transport SAR** (mesh-level): Splits Access messages across Network PDUs when payload > 15 bytes

These must not be conflated. The Proxy layer is transparent to the mesh stack -- it reassembles Network PDUs before passing them up.

### 8.3 Bearer Abstraction

```kotlin
internal interface MeshBearer : AutoCloseable {
    val incomingPdus: Flow<NetworkPdu>    // Reassembled Network PDUs
    val isOpen: StateFlow<Boolean>
    suspend fun send(pdu: NetworkPdu)     // Single Network PDU (proxy SAR handled internally)
    suspend fun open()
}

// GATT Proxy - the ONLY TX-capable bearer on smartphone
internal class GattProxyBearer(
    private val peripheral: Peripheral,
    private val proxyFilter: ProxyFilter = ProxyFilter.AcceptAll,
) : MeshBearer

// ADV Bearer - RX ONLY for unprovisioned device beacons
internal class AdvScanBearer(
    private val scanner: Scanner,
) : MeshBearer {
    // send() throws UnsupportedOperationException - phone cannot TX on ADV bearer
}
```

---

## 9. Message Flow Architecture

### 9.1 Send Flow (Outbound)

```mermaid
flowchart TD
    A[App calls model.set node, true] --> B[GenericOnOffClient]
    B --> C["AccessLayer.encode<br/>Opcode: 0x8202 (OnOff Set)<br/>Params: [0x01] (ON)"]
    C --> D["UpperTransportLayer.encrypt<br/>AES-128-CCM with AppKey<br/>64-bit TransportMIC"]
    D --> E{"Payload > 15 bytes?"}
    E -->|No| F["Single Upper Transport PDU"]
    E -->|Yes| G["LowerTransportLayer.segment<br/>Split into N segments<br/>Each: 12 bytes payload + seg header"]
    G --> H["Block ACK tracking<br/>(for acknowledged msgs)"]
    H --> F
    F --> I["NetworkLayer.encrypt<br/>AES-128-CCM with NetKey<br/>Add privacy obfuscation<br/>Set TTL, SEQ, SRC, DST"]
    I --> J["ProxyClient.sendPdu<br/>Apply Proxy SAR if needed<br/>Write to GATT Data In char"]
    J --> K[Proxy Node receives]
```

### 9.2 Receive Flow (Inbound)

```mermaid
flowchart TD
    A[GATT Notification<br/>from Proxy Data Out] --> B["ProxyClient<br/>Reassemble Proxy SAR<br/>Extract Network PDU type"]
    B --> C{Message Type?}
    C -->|Network PDU| D["NetworkLayer.decrypt<br/>Find NetKey by NID<br/>AES-128-CCM decrypt<br/>Verify NetMIC<br/>Check replay protection"]
    C -->|Mesh Beacon| E["Process Beacon<br/>Update IV Index if needed<br/>Track network health"]
    C -->|Proxy Config| F["Proxy Filter Response<br/>Update filter state"]
    D --> G{"CTL bit?"}
    G -->|Access Message| H["UpperTransportLayer.decrypt<br/>AES-128-CCM with AppKey<br/>Verify TransportMIC"]
    G -->|Control Message| I["Process Transport Control<br/>SAR ACK, Heartbeat,<br/>Friend Poll response"]
    H --> J{"Segmented? (SEQZ field)"}
    J -->|Yes| K["LowerTransportLayer.reassemble<br/>Collect segments by SeqZero<br/>Timeout: 20s<br/>Send Block ACK"]
    J -->|No| L["Single Access PDU"]
    K --> L
    L --> M["AccessLayer.decode<br/>Parse opcode + params"]
    M --> N["ModelDispatcher.route<br/>Match opcode → registered handler"]
    N --> O["Handler invoked<br/>e.g. GenericOnOffServer.setState(true)"]
    O --> P["Model emits status<br/>via StateFlow / Flow"]
```

### 9.3 Message Acknowledgment Pattern

```mermaid
sequenceDiagram
    participant Client as GenericOnOffClient
    participant Net as MeshNetwork
    participant Proxy as ProxyClient
    participant Server as Remote OnOff Server

    Client->>Net: set(node, true)
    Net->>Net: encode: OnOff Set (acknowledged)
    Note over Net: Set TTL=5, SEQ=42
    Net->>Proxy: sendPdu(networkPdu)

    Proxy->>Server: GATT write → Proxy Node → Mesh Network
    Note over Server: Relay nodes forward PDU

    alt Acknowledged Message
        Server-->>Net: OnOff Status (present=1, target=1)
        Note over Net: Response SEQ=0x1234
        Net-->>Client: GenericOnOffStatus(present=true)
    else Timeout (10s default)
        Net-->>Client: throws MeshTimeoutException
    end
```

---

## 10. Concurrency & Threading Model

```mermaid
graph TB
    subgraph "Per-Proxy Connection"
        direction TB
        GATT[GATT Callback Thread<br/>Platform-specific<br/>Android: HandlerThread<br/>iOS: DispatchQueue]
        PD[Proxy Dispatcher<br/>Dispatchers.Default<br/>.limitedParallelism(1)]
        CH[Channel<br/>UNLIMITED capacity<br/>Buffered to prevent<br/>backpressure on GATT thread]
    end

    subgraph "Shared Network State"
        MUT[Mutex<br/>Guards all shared state]
        STATE[MeshNetworkState<br/>nodes, keys, IV Index<br/>sequence numbers]
    end

    subgraph "Consumer Coroutines"
        C1[Model API caller<br/>e.g. onOff.set()]
        C2[Message observer<br/>e.g. incomingMessages.collect{}]
        C3[Provisioner caller<br/>e.g. provisioner.provision()]
    end

    GATT -->|"CompletableDeferred.complete()"| CH
    CH -->|"receiveAsFlow()"| PD
    PD -->|"Mutex.withLock{}"| MUT
    MUT --> STATE

    C1 -->|"suspend fun<br/>runs on caller's context"| MUT
    C2 -->|"Flow.collect<br/>runs on collector's context"| MUT
    C3 -->|"suspend fun<br/>runs on caller's context"| MUT
```

**Key concurrency rules:**
1. GATT callbacks complete `CompletableDeferred` values -- they NEVER touch shared state directly
2. The proxy dispatcher (`limitedParallelism(1)`) serializes all proxy-bound operations: writes, SAR reassembly, PDU processing
3. Shared network state is guarded by `Mutex` -- both the proxy dispatcher and consumer coroutines acquire it
4. Sequence number allocation is atomic via `AtomicInt` (atomicfu) -- no mutex needed for increment-only counter
5. Consumer API calls run on the caller's coroutine context, acquiring the mutex only when reading/writing shared state

---

## 11. Sequence Number & IV Index Management

### 11.1 Sequence Number Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Normal: SEQ = 0
    Normal --> Normal: SEQ++ on each message send
    Normal --> Warning: SEQ approaches 0xFFFFFF (16.7M)
    Warning --> IVUpdateRequired: SEQ at 95% capacity
    IVUpdateRequired --> [*]: IV Index update procedure<br/>resets all SEQ to 0
    Normal --> Persisted: save to MeshStateStore
    Persisted --> Normal: restore on next app launch
```

**Critical invariant:** A (SEQ, IV Index, Source Address) tuple MUST never repeat. If it does, other mesh nodes will reject the message as a replay attack and may blacklist the source.

### 11.2 IV Index Update Procedure

```mermaid
sequenceDiagram
    participant Net as Mesh Network
    participant Phone as Smartphone (our node)
    participant Proxy as Proxy Node
    participant Mesh as Rest of Mesh

    Note over Mesh: Network-wide IV Update begins
    Mesh-->>Proxy: Secure Network Beacon<br/>IV Index: N+1, Flag: IV Update Active
    Proxy-->>Phone: Network PDU with IVI=1<br/>(using new IV Index)

    Note over Phone: Enter IV Update In Progress state
    Phone->>Phone: Accept PDUs with IV=N or IV=N+1
    Phone->>Phone: Send PDUs with IV=N (old)<br/>until we receive SNB with IV Update Active=0

    Mesh-->>Proxy: Secure Network Beacon<br/>IV Index: N+1, Flag: Normal
    Proxy-->>Phone: SNB confirms update complete

    Note over Phone: Transition to Normal state
    Phone->>Phone: set IV Index = N+1
    Phone->>Phone: reset all sequence numbers to 0
    Phone->>Phone: persist new IV Index + seq numbers
    Phone->>Phone: Send PDUs with IV=N+1 (new)
```

### 11.3 Replay Protection List (RPL)

```kotlin
internal class ReplayProtectionList(
    private val capacity: Int = 256,  // per source address
) {
    // Stores seen (seq, ivIndex) pairs for each source address
    // Any message with seq <= lastSeen for the same IV Index is REJECTED
    // New IV Index automatically clears the old entries
    fun checkAndUpdate(src: UnicastAddress, seq: UInt, ivi: Int): Boolean
    fun evictOldest(src: UnicastAddress)
}
```

---

## 12. Persistence Architecture

### 12.1 What Must Be Persisted (and Why)

| Data | Criticality | Consequence of Loss |
|------|------------|-------------------|
| **Sequence Numbers** | **CRITICAL** | Node permanently rejected from network (replay protection) |
| **IV Index** | **CRITICAL** | Cannot decrypt any network messages |
| **Network Keys** | High | Cannot participate in network (must re-provision) |
| **Application Keys** | High | Cannot send/receive model messages |
| **Device Keys** | High | Cannot configure nodes |
| **Node List** | Medium | Must re-discover nodes (via composition data) |
| **Composition Data** | Low | Can re-fetch from each node |

### 12.2 Persistence Interface

```kotlin
public interface MeshStateStore {
    /**
     * Atomically save the complete network state.
     * Called on every sequence number change and IV Index update.
     * Implementation MUST be durable before returning.
     */
    suspend fun saveNetworkState(state: MeshNetworkState): Result<Unit>

    /**
     * Load the last saved network state.
     * Returns null if no state exists (first launch).
     */
    suspend fun loadNetworkState(): Result<MeshNetworkState?>

    /**
     * Clear all persisted state.
     * Called when leaving a mesh network.
     */
    suspend fun clearAll(): Result<Unit>
}

public data class MeshNetworkState(
    val ivIndex: IvIndex,
    val unicastAddress: UnicastAddress,
    val networkKeys: List<NetworkKey>,
    val applicationKeys: List<ApplicationKey>,
    val nodes: List<PersistedNodeState>,
)

public data class PersistedNodeState(
    val unicastAddress: UnicastAddress,
    val deviceKey: DeviceKey,
    val lastSequenceNumber: UInt,
    val features: NodeFeatures,
)
```

### 12.3 Atomic Persistence Strategy

```mermaid
flowchart TD
    A[Sequence number incremented] --> B[Update in-memory state]
    B --> C[Call MeshStateStore.saveNetworkState]
    C --> D{Save successful?}
    D -->|Yes| E[Continue operation]
    D -->|No| F[Log error<br/>Retry with backoff]
    F --> G{Retry succeeded?}
    G -->|Yes| E
    G -->|No, max retries| H["CRITICAL: persist in-memory<br/>Do NOT send more messages<br/>until persistence recovers"]
```

**The cardinal rule:** Never send a mesh message whose sequence number has not been durably persisted. Losing a sequence number means permanent exclusion from the network.

---

## 13. Model Dispatch Architecture

### 13.1 Opcode Registry

```mermaid
graph TD
    subgraph "Model Dispatcher"
        Registry[OpcodesRegistry<br/>Map~Int, ModelHandler~]
        Router[MessageRouter<br/>dispatch(address, opcode, params)]
    end

    subgraph "Registered Handlers"
        H1[GenericOnOffServer<br/>opcodes: 0x8201-0x8204, 0x82E4]
        H2[GenericLevelServer<br/>opcodes: 0x8205-0x820A, 0x82E5-0x82E6]
        H3[ConfigurationServer<br/>opcodes: 0x8000-0x803F]
        H4[HealthServer<br/>opcodes: 0x803E-0x804D]
        H5[VendorModel<br/>opcode: 0xC00000 +]
    end

    Router --> Registry
    Registry --> H1
    Registry --> H2
    Registry --> H3
    Registry --> H4
    Registry --> H5
```

### 13.2 Dispatch Flow

```kotlin
internal class MessageRouter(
    private val registry: OpcodeRegistry,
    private val network: MeshNetwork,
) {
    suspend fun dispatch(
        source: UnicastAddress,
        destination: MeshAddress,
        opcode: MeshOpcode,
        params: ByteArray,
        appKey: ApplicationKey?,
    ) {
        val handler = registry.find(opcode)
            ?: run {
                // Unknown opcode - ignore silently per mesh spec
                // (don't NACK, that would flood the network)
                return
            }

        // Verify destination matches our address
        when (destination) {
            is UnicastAddress -> {
                if (destination != network.ownUnicastAddress) return
            }
            is GroupAddress -> {
                // Check if we subscribe to this group
                if (!network.isSubscribed(destination)) return
            }
            is VirtualAddress -> {
                // Check virtual address subscription
                if (!network.isSubscribed(destination)) return
            }
        }

        // Route to handler on the correct element
        handler.handle(source, destination, opcode, params, appKey)
    }
}
```

---

## 14. Standard Models

### 14.1 Model Client Pattern

```kotlin
// All model clients follow this pattern:
public class GenericOnOffClient internal constructor(
    private val network: MeshNetwork,
    private val appKey: ApplicationKey,
) {
    public suspend fun get(elementAddress: UnicastAddress): GenericOnOffStatus
    public suspend fun set(elementAddress: UnicastAddress, state: Boolean,
                           transitionTime: TransitionTime? = null): GenericOnOffStatus
    public suspend fun setUnacknowledged(elementAddress: UnicastAddress, state: Boolean)
    public fun onStatusChanged(elementAddress: UnicastAddress): Flow<GenericOnOffStatus>
}

public data class GenericOnOffStatus(
    val presentOnOff: Boolean,
    val targetOnOff: Boolean? = null,
    val remainingTime: TransitionTime? = null,
)

// Transition time encoding for smooth dimming:
public value class TransitionTime(val milliseconds: UInt) {
    val encodedValue: UByte  // 6-bit resolution + 2-bit step encoding
}
```

### 14.2 Model Server Pattern

```kotlin
public class GenericOnOffServer internal constructor(
    private val element: MeshElement,
    private val appKey: ApplicationKey,
) {
    /** Current state exposed as StateFlow for UI binding */
    public val state: StateFlow<Boolean>

    /** Set state locally (for local control) */
    public suspend fun setState(on: Boolean)

    /** Handle incoming GET/SET from remote clients */
    internal suspend fun handleMessage(
        source: UnicastAddress,
        opcode: MeshOpcode,
        params: ByteArray,
    ): ByteArray?  // null for unacknowledged, response bytes for acknowledged
}
```

---

## 15. Error Architecture

```kotlin
// Following the kmp-ble error pattern: composable sealed interfaces
public sealed interface MeshError

// --- Provisioning ---
public sealed interface ProvisioningError : MeshError
public data class ProvisioningFailed(
    val phase: ProvisioningPhase,
    val reason: String,
    val recoveryHint: String = "Retry provisioning. Verify device is in range and not already provisioned.",
) : ProvisioningError

public data class ProvisioningTimeout(
    val timeout: Duration,
    val recoveryHint: String = "Increase timeout or verify device responsiveness.",
) : ProvisioningError

public data class OobAuthenticationFailed(
    val method: String,
    val recoveryHint: String = "Verify the OOB key/display matches the device.",
) : ProvisioningError

// --- Transport ---
public sealed interface MeshTransportError : MeshError
public data class ProxyConnectionFailed(
    val reason: String,
    val recoveryHint: String = "Verify proxy node is in range and has proxy feature enabled.",
) : MeshTransportError

public data class ProxyDisconnected(
    val reason: String,
    val recoveryHint: String = "Reconnecting... If persistent, try another proxy node.",
) : MeshTransportError

public data class MessageTimeout(
    val operation: String,
    val timeout: Duration,
    val recoveryHint: String = "No response from mesh node. Check device is online.",
) : MeshTransportError

// --- Crypto ---
public sealed interface MeshCryptoError : MeshError
public data class DecryptionFailed(
    val details: String,
    val recoveryHint: String = "Possible IV Index mismatch. Verify network keys.",
) : MeshCryptoError

// --- Configuration ---
public sealed interface ConfigurationError : MeshError
public data class ConfigurationRejected(
    val opcode: Int,
    val statusCode: UByte,
    val recoveryHint: String = "Configuration command rejected by node.",
) : ConfigurationError

// --- Platform ---
public data class MeshNotSupported(
    val message: String = "BLE Mesh is not supported on this platform",
) : Exception(message), MeshError

// --- Exception wrapper (following BleException pattern) ---
public data class MeshException(
    public val error: MeshError,
) : Exception(error.toString())
```

---

## 16. Testing Architecture

### 16.1 Fake Hierarchy

```mermaid
classDiagram
    class FakeMeshNetwork {
        +MutableStateFlow nodes
        +MutableStateFlow ivIndex
        +addNode(node)
        +simulateIncomingMessage(msg)
        +getSentMessages(): List
        +simulateDisconnect()
    }
    class FakeMeshNode {
        +unicastAddress
        +elements
        +deviceKey
        +features
        +queueResponse(opcode, response)
    }
    class FakeProvisioner {
        +scanEvents: MutableSharedFlow
        +queueProvisioningResult(node)
        +simulateBearerError(error)
        +simulateProvisioningProgress(progress)
        +getLastProvisionRequest()
    }
    class FakeProxyClient {
        +incomingPdus: Channel
        +sendLog: List
        +connected: MutableStateFlow
        +simulatePdu(pdu)
        +simulateDisconnect()
    }
    class FakeMeshStateStore {
        +savedStates: List
        +loadResult: Result
        +saveFails: Boolean
    }

    MeshNetwork <|.. FakeMeshNetwork
    MeshProvisioner <|.. FakeProvisioner
    ProxyConnection <|.. FakeProxyClient
    MeshStateStore <|.. FakeMeshStateStore
```

### 16.2 Test Pyramid

```mermaid
graph TB
    subgraph "Integration Tests (commonTest + device)"
        INT[End-to-end:<br/>Provision → Configure → Message<br/>via FakePeripheral + FakeProxyClient]
    end
    subgraph "Conformance Tests (commonTest)"
        CONF[Abstract conformance tests<br/>Platform runners in jvmTest/iosTest]
    end
    subgraph "Unit Tests (commonTest)"
        UNIT1[Crypto: test vectors<br/>from SIG spec + RFC 4493]
        UNIT2[Protocol: state machines<br/>Provisioning, SAR, IV Update]
        UNIT3[PDU: encode/decode<br/>round-trips with edge cases]
        UNIT4[Models: message format<br/>encode/decode + state mgmt]
    end

    INT --> CONF
    CONF --> UNIT1
    CONF --> UNIT2
    CONF --> UNIT3
    CONF --> UNIT4
```

### 16.3 Key Test Scenarios

| Test | What it validates | Uses |
|------|------------------|------|
| `AesCcmTest` | AES-128-CCM against SIG spec test vectors (Annex C) | Pure Kotlin implementation |
| `CmacTest` | AES-128-CMAC against RFC 4493 test vectors | Pure Kotlin implementation |
| `KeyDerivationTest` | k1/k2/k3 with known test vectors | Correct key derivation |
| `ProvisioningStateMachineTest` | Full provisioning flow with NoOob/StaticOob | State transitions + crypto |
| `TransportSarTest` | Segmentation of 200-byte payload, out-of-order reassembly, timeout | Lower transport layer |
| `NetworkLayerTest` | PDU encrypt/decrypt, privacy obfuscation, TTL handling, replay detection | Network layer |
| `ProxySarTest` | Proxy-level SAR with various MTU sizes | Proxy client |
| `IvUpdateTest` | IV Index update procedure: accept old+new, reject old after transition | IV Index tracker |
| `SequenceNumberTest` | Sequence number overflow, RPL eviction | Sequence number manager |
| `ModelDispatchTest` | Opcode routing to correct handler, group/virtual address filtering | Message router |
| `PersistenceTest` | Save/load network state, sequence number recovery | MeshStateStore |
| `ConformanceTest` | Abstract test class with Fake* factories, platform runners | Full stack |

---

## 17. Phased Implementation Plan

| Phase | Deliverables | Key Files | LOC | Risk |
|-------|-------------|-----------|-----|------|
| **Phase 1: Foundation** | Core types (address, key, node, element, model, PDU), crypto primitives (AES-CCM, CMAC, ECDH, SHA-256, key derivation), provisioning state machine (PB-ADV + PB-GATT), error hierarchy | ~18 | 1500-2000 | Crypto correctness, provisioning interop |
| **Phase 2: Connectivity** | Network layer (encrypt/decrypt, privacy, relay), transport layers (SAR + app encrypt), access layer (opcode format), GATT proxy client (connect, SAR, filter), basic send/receive | ~14 | 2000-2500 | SAR complexity, proxy interop |
| **Phase 3: Management** | Configuration client (AppKey, publish, subscribe, features), Health model, Composition data parsing, IV Index tracker, RPL | ~8 | 1200-1500 | Config state machine correctness |
| **Phase 4: Usability** | Generic OnOff/Level/Sensor clients, MeshStateStore SPI + InMemory impl, Model dispatcher, Vendor model registration, integration tests, sample | ~12 | 1500-2000 | Model interop, persistence durability |
| **Total** | | **~52** | **6200-8000** | |

---

## 18. Key Design Decisions & Trade-offs

| Decision | Pros | Cons | Chosen Because |
|----------|------|------|----------------|
| **Pure Kotlin AES-128-CCM** | Single implementation, portable, auditable, deterministic behavior across platforms | ~2-5x slower than hardware AES (negligible for 30-byte PDUs) | iOS lacks CCM API; single code path avoids platform-specific CCM bugs |
| **Asymmetric bearer (RX ADV + GATT, TX GATT only)** | Matches platform reality, type-safe (can't accidentally TX on ADV) | Abstraction is not clean -- `send()` on ADV bearer throws | Platform constraint, not design choice. Making it explicit prevents bugs |
| **SPI persistence vs built-in** | Consumers own storage; can use secure enclave/keychain for keys | More work for consumer; risk of incorrect implementation | Security-critical data (keys) should be in platform-secure storage. Library can't know the right backend |
| **`limitedParallelism(1)` per proxy** | No locks, simple reasoning, matches core library pattern | One slow operation blocks all proxy traffic (mitigated by timeouts) | Proven pattern; mesh message latency is dominated by BLE connection interval (7.5-30ms), not dispatcher overhead |
| **`Mutex` on shared state** | Simple, familiar to Kotlin devs, composable with suspend | Potential contention if many concurrent callers (unlikely for BLE) | Mesh operations are inherently serial (one proxy connection). Contention is theoretical, not practical |
| **Manual models before codegen** | Flexible API design, quick iteration | Boilerplate for each new model | Early stage needs API exploration; codegen ROI increases with model count |
| **Single proxy connection (phase 1-3)** | Simple connection management, single source of truth for IV Index | Single point of failure; proxy node outage = phone offline | 95% of use cases have one proxy in range. Multi-proxy adds non-trivial complexity (duplicate PDU detection, dual IV Index sources) |

---

## 19. Risk Assessment

```mermaid
graph TD
    subgraph "High Risk"
        R1["Crypto correctness<br/>Mitigation: SIG spec test vectors<br/>for every primitive"]
        R2["Sequence number durability<br/>Mitigation: Atomic save-before-send<br/>Consumer docs emphasize criticality"]
        R3["Proxy interop with real nodes<br/>Mitigation: Test against Nordic nRF<br/>and Espressif ESP32 proxy nodes"]
    end
    subgraph "Medium Risk"
        R4["IV Index update handling<br/>Mitigation: Test old+new acceptance<br/>window; spec-compliant state machine"]
        R5["iOS GATT notification reliability<br/>Mitigation: Core kmp-ble observation<br/>resilience already handles this"]
        R6["Segmented message timeout<br/>Mitigation: 20s default, configurable<br/>per-network"]
    end
    subgraph "Low Risk"
        R7["PB-ADV discovery reliability<br/>Mitigation: PB-GATT is primary<br/>provisioning path for mobile"]
        R8["OOB UX complexity<br/>Mitigation: NoOob default for dev<br/>devices; clear docs for production"]
    end

    style R1 fill:#ff6b6b
    style R2 fill:#ff6b6b
    style R3 fill:#ff6b6b
    style R4 fill:#ffd93d
    style R5 fill:#ffd93d
    style R6 fill:#ffd93d
    style R7 fill:#6bff6b
    style R8 fill:#6bff6b
```

---

## 20. Build Configuration

```kotlin
// kmp-ble-mesh/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
}

group = "com.atruedev"
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-local")

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }

    android {
        namespace = "com.atruedev.kmpble.mesh"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        withHostTestBuilder {}.configure {}
    }

    jvm()

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "KmpBleMesh"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("kmp-ble-mesh")
        includes.from("MODULE.md")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.atruedev", "kmp-ble-mesh", version.toString())
    pom {
        name.set("kmp-ble-mesh")
        description.set("BLE Mesh networking support for kmp-ble - provisioning, proxy, models")
        url.set("https://github.com/gary-quinn/kmp-ble")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("gary-quinn")
                name.set("Gary Quinn")
                email.set("gary@atruedev.com")
            }
        }
        scm {
            url.set("https://github.com/gary-quinn/kmp-ble")
        }
    }
}
```

**Registration:** `include(":kmp-ble-mesh")` in `settings.gradle.kts`

---

## 21. Usage Example

```kotlin
// === 1. Create mesh network ===
val network = MeshNetwork {
    networkKey(myNetKey)
    applicationKey(myAppKey)
    element(MeshElement(
        index = 0,
        unicastAddress = UnicastAddress(0x0001u),
        location = ElementLocation.MAIN,
        models = listOf(MeshModelId.GenericOnOffClient),
    ))
    stateStore(platformStateStore)  // your platform's secure storage impl
}

// === 2. Discover and provision devices ===
val provisioner = MeshProvisioner()
provisioner.scanEvents
    .filter { it.bearerType == ProvisioningBearerType.PB_GATT }
    .collect { device ->
        println("Found unprovisioned device: ${device.uuid}")

        try {
            val node = provisioner.provision(
                device = device,
                networkKey = myNetKey,
                unicastAddress = allocateNextAddress(),
                oobAuth = OobAuthentication.None,
            )
            network.addNode(node)
            println("Provisioned: ${node.unicastAddress}")

            // === 3. Configure node ===
            val config = network.configurationClient
            config.addAppKey(node, myAppKey,
                listOf(MeshModelId.GenericOnOffServer))
            config.setPublication(node,
                MeshModelId.GenericOnOffServer,
                network.ownUnicastAddress, myAppKey.index)
            config.addSubscription(node,
                MeshModelId.GenericOnOffServer,
                GroupAddress(0xC001u))
        } catch (e: MeshException) {
            when (e.error) {
                is ProvisioningFailed -> println("Failed: ${e.error.reason}")
                is OobAuthenticationFailed -> println("OOB mismatch!")
                else -> throw e
            }
        }
    }

// === 4. Connect to mesh via proxy ===
val proxyPeripheral = /* discovered via Scanner or known address */
val proxy = network.connectProxy(proxyPeripheral)
println("Connected to mesh via proxy: ${proxy.isConnected.value}")

// === 5. Use model APIs ===
val onOff = GenericOnOffClient(network, myAppKey)
val lightNode = network.findNode(UnicastAddress(0x0005u))!!

// Read current state
val status = onOff.get(lightNode.unicastAddress)
println("Light is: ${if (status.presentOnOff) "ON" else "OFF"}")

// Turn on
onOff.set(lightNode.unicastAddress, true)

// Dim over 2 seconds
onOff.set(lightNode.unicastAddress, true, TransitionTime(2000u))

// Observe status changes (e.g., from physical switch)
onOff.onStatusChanged(lightNode.unicastAddress).collect { update ->
    println("Light changed: ${if (update.presentOnOff) "ON" else "OFF"}")
}

// === 6. Cleanup ===
network.close()  // disconnects proxy, releases all resources
```

---

## 22. References

| Resource | URL |
|----------|-----|
| Bluetooth Mesh Protocol v1.1 | https://www.bluetooth.com/specifications/mesh-specification/ |
| Bluetooth Mesh Model v1.1 | https://www.bluetooth.com/specifications/mesh-model-specification/ |
| Mesh Security Overview v1.0 (2025) | https://www.bluetooth.com/wp-content/uploads/2025/04/MeshSecurityOverview_INFO_v1.0-1.pdf |
| Nordic nRF Mesh Library (Android) | https://github.com/nordicsemiconductor/Android-nRF-Mesh-Library |
| Nordic nRF Mesh Library (iOS) | https://github.com/nordicsemiconductor/IOS-nRF-Mesh-Library |
| Silicon Labs Bluetooth Mesh ADK | https://docs.silabs.com/btmesh/9.0.2/ |
| kmp-ble Architecture | `ARCHITECTURE.md` |
| kmp-ble GATT Server DSL | `GattServerBuilder.kt` |
| kmp-ble DFU Module Pattern | `kmp-ble-dfu/build.gradle.kts`, `DfuController.kt` |
| kmp-ble Fake Pattern | `FakePeripheral.kt`, `FakeDfuTransport.kt` |
