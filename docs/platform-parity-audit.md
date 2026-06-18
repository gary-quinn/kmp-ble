# Platform Parity Audit

> Generated 2026-06-19. androidMain: 45 files, 4,566 LOC | iosMain: 37 files, 4,015 LOC

## File Inventory

### androidMain (45 files)

| File | Category | iOS Equivalent |
|------|----------|---------------|
| `BleData.android.kt` | Data | `BleData.ios.kt` |
| `KmpBle.android.kt` | Init | NONE (platform necessity) |
| `KmpBleInitializer.kt` | Init | NONE (platform necessity) |
| `adapter/AdapterFactory.android.kt` | Adapter | `adapter/AdapterFactory.ios.kt` |
| `adapter/AndroidBluetoothAdapter.kt` | Adapter | `adapter/IosBluetoothAdapter.kt` |
| `connection/StateRestorationApi.android.kt` | Connection | `connection/StateRestorationApi.ios.kt` |
| `gatt/cache/AndroidGattCache.kt` | Cache | `gatt/cache/IosGattCache.kt` |
| `gatt/internal/ObservationPersistence.android.kt` | GATT | `gatt/internal/ObservationPersistence.ios.kt` |
| `isochronous/IsochronousListenerFactory.android.kt` | LE Audio | `isochronous/IsochronousListenerFactory.ios.kt` |
| `l2cap/AndroidL2capChannel.kt` | L2CAP | `l2cap/IosL2capChannel.kt` |
| `l2cap/AndroidL2capListener.kt` | L2CAP | `l2cap/IosL2capListener.kt` |
| `l2cap/BluetoothL2capSocket.kt` | L2CAP | NONE (Android abstraction) |
| `l2cap/L2capListenerFactory.android.kt` | L2CAP | `l2cap/L2capListenerFactory.ios.kt` |
| `l2cap/L2capSocket.kt` | L2CAP | NONE (Android abstraction) |
| `peripheral/AndroidBondManager.kt` | Bonding | NONE (iOS: NotSupported) |
| `peripheral/AndroidGattBridge.kt` | Peripheral | NONE (iOS: ApplePeripheralBridge) |
| `peripheral/AndroidGattStatusMapper.kt` | Peripheral | `peripheral/IosGattStatusMapper.kt` |
| `peripheral/AndroidPairingRequestHandler.kt` | Bonding | NONE (iOS: NotSupported) |
| `peripheral/AndroidPeripheral.kt` | Peripheral | `peripheral/IosPeripheral.kt` |
| `peripheral/AndroidPeripheralConnection.kt` | Peripheral | `peripheral/IosPeripheralConnection.kt` |
| `peripheral/AndroidPeripheralGattHandler.kt` | Peripheral | NONE (handled inline) |
| `peripheral/AndroidPeripheralInternal.kt` | Peripheral | `peripheral/IosPeripheralInternal.kt` |
| `peripheral/AndroidPeripheralL2cap.kt` | Peripheral | `peripheral/IosPeripheralL2cap.kt` |
| `peripheral/PeripheralFactory.android.kt` | Peripheral | `peripheral/PeripheralFactory.ios.kt` |
| `permissions/BlePermissions.android.kt` | Permissions | `permissions/BlePermissions.ios.kt` |
| `quirks/BleQuirks.kt` | Quirks | **MISSING (#259)** |
| `quirks/DeviceInfo.kt` | Quirks | **MISSING (#259)** |
| `quirks/DeviceMatch.kt` | Quirks | **MISSING (#259)** |
| `quirks/QuirkKey.kt` | Quirks | **MISSING (#259)** |
| `quirks/QuirkProvider.kt` | Quirks | **MISSING (#259)** |
| `quirks/QuirkRegistry.kt` | Quirks | **MISSING (#259)** |
| `scanner/AndroidAdvertisementParser.kt` | Scanner | `scanner/IosAdvertisementParser.kt` |
| `scanner/AndroidScanner.kt` | Scanner | `scanner/IosScanner.kt` |
| `scanner/ScannerFactory.android.kt` | Scanner | `scanner/ScannerFactory.ios.kt` |
| `server/AndroidAdvertiser.kt` | Server | `server/IosAdvertiser.kt` |
| `server/AndroidExtendedAdvertiser.kt` | Server | `server/IosExtendedAdvertiser.kt` |
| `server/AndroidGattServer.kt` | Server | `server/IosGattServer.kt` |
| `server/AndroidGattServerCallback.kt` | Server | NONE (iOS: delegate callback) |
| `server/AndroidGattServerConnectionHandlers.kt` | Server | NONE (in IosGattServerHandlers) |
| `server/AndroidGattServerExtensions.kt` | Server | `server/IosGattServerExtensions.kt` |
| `server/AndroidGattServerReadHandlers.kt` | Server | NONE (in IosGattServerHandlers) |
| `server/AndroidGattServerSetup.kt` | Server | NONE (inline) |
| `server/AndroidGattServerState.kt` | Server | NONE (inline) |
| `server/AndroidGattServerWriteHandlers.kt` | Server | NONE (in IosGattServerHandlers) |
| `server/GattServerFactory.android.kt` | Server | `server/GattServerFactory.ios.kt` |

### iosMain (37 files)

| File | Category | Android Equivalent |
|------|----------|-------------------|
| `BleData.ios.kt` | Data | `BleData.android.kt` |
| `adapter/AdapterFactory.ios.kt` | Adapter | `adapter/AdapterFactory.android.kt` |
| `adapter/IosBluetoothAdapter.kt` | Adapter | `adapter/AndroidBluetoothAdapter.kt` |
| `connection/StateRestorationApi.ios.kt` | Connection | `connection/StateRestorationApi.android.kt` |
| `gatt/cache/IosGattCache.kt` | Cache | `gatt/cache/AndroidGattCache.kt` |
| `gatt/internal/ObservationPersistence.ios.kt` | GATT | `gatt/internal/ObservationPersistence.android.kt` |
| `internal/CentralDelegate.kt` | Internal | NONE (platform necessity) |
| `internal/CentralDelegateImpl.kt` | Internal | NONE (platform necessity) |
| `internal/CentralDelegateState.kt` | Internal | NONE (platform necessity) |
| `internal/CentralManagerProvider.kt` | Internal | NONE (platform necessity) |
| `internal/IosPeripheralManagerDelegate.kt` | Internal | NONE (platform necessity) |
| `internal/KmpBleCentralDelegateObjC.kt` | Internal | NONE (platform necessity) |
| `internal/PeripheralManagerProvider.kt` | Internal | NONE (platform necessity) |
| `internal/StateRestorationHandler.kt` | Internal | NONE (platform necessity) |
| `isochronous/IsochronousListenerFactory.ios.kt` | LE Audio | `isochronous/IsochronousListenerFactory.android.kt` |
| `l2cap/IosL2capChannel.kt` | L2CAP | `l2cap/AndroidL2capChannel.kt` |
| `l2cap/IosL2capListener.kt` | L2CAP | `l2cap/AndroidL2capListener.kt` |
| `l2cap/L2capListenerFactory.ios.kt` | L2CAP | `l2cap/L2capListenerFactory.android.kt` |
| `peripheral/ApplePeripheralBridge.kt` | Peripheral | `peripheral/AndroidGattBridge.kt` |
| `peripheral/IosGattStatusMapper.kt` | Peripheral | `peripheral/AndroidGattStatusMapper.kt` |
| `peripheral/IosPeripheral.kt` | Peripheral | `peripheral/AndroidPeripheral.kt` |
| `peripheral/IosPeripheralBridgeHandlers.kt` | Peripheral | NONE (handled inline) |
| `peripheral/IosPeripheralConnection.kt` | Peripheral | `peripheral/AndroidPeripheralConnection.kt` |
| `peripheral/IosPeripheralDiscovery.kt` | Peripheral | `peripheral/AndroidPeripheralGattHandler.kt` |
| `peripheral/IosPeripheralInternal.kt` | Peripheral | `peripheral/AndroidPeripheralInternal.kt` |
| `peripheral/IosPeripheralL2cap.kt` | Peripheral | `peripheral/AndroidPeripheralL2cap.kt` |
| `peripheral/PeripheralFactory.ios.kt` | Peripheral | `peripheral/PeripheralFactory.android.kt` |
| `permissions/BlePermissions.ios.kt` | Permissions | `permissions/BlePermissions.android.kt` |
| `scanner/IosAdvertisementParser.kt` | Scanner | `scanner/AndroidAdvertisementParser.kt` |
| `scanner/IosAdvertisementReconstruction.kt` | Scanner | NONE (platform necessity) |
| `scanner/IosScanner.kt` | Scanner | `scanner/AndroidScanner.kt` |
| `scanner/ScannerFactory.ios.kt` | Scanner | `scanner/ScannerFactory.android.kt` |
| `server/GattServerFactory.ios.kt` | Server | `server/GattServerFactory.android.kt` |
| `server/IosAdvertiser.kt` | Server | `server/AndroidAdvertiser.kt` |
| `server/IosExtendedAdvertiser.kt` | Server | `server/AndroidExtendedAdvertiser.kt` |
| `server/IosGattServer.kt` | Server | `server/AndroidGattServer.kt` |
| `server/IosGattServerExtensions.kt` | Server | `server/AndroidGattServerExtensions.kt` |
| `server/IosGattServerHandlers.kt` | Server | NONE (split into 5 handler files) |

## Gap Classification

### Feature Gaps (needs implementation)

| Gap | Android | iOS | Ticket |
|-----|---------|-----|--------|
| Device quirk detection | `quirks/` (6 files): QuirkRegistry, BleQuirks, DeviceInfo, DeviceMatch, QuirkKey, QuirkProvider | Missing | #259 |
| BondManager API | `AndroidBondManager.kt`: createBond, removeBond, bondState flow | IosPeripheral.removeBond() returns NotSupported | NONE (by design) |
| PairingRequestHandler | `AndroidPairingRequestHandler.kt`: PIN/passkey/auto-accept | Missing (iOS: pairing handled by OS) | NONE (by design) |

### Platform Necessities (legitimate asymmetry)

| Pattern | Android | iOS |
|---------|---------|-----|
| Internal delegates | Uses Binder callbacks (no internal dir) | 8 internal files: CoreBluetooth delegate pattern requires ObjC bridging |
| Init system | `KmpBle.android.kt` + `KmpBleInitializer.kt` (AndroidX Startup) | Not needed: CBCentralManager auto-initializes |
| L2CAP socket abstraction | `BluetoothL2capSocket` + `L2capSocket` | Not needed: CBL2CAPChannel is a single class |
| Advertisement parsing | Single-pass parser | Two-pass: `IosAdvertisementParser` + reconstruction from service data |
| GattServer decomposition | 11 files (decomposed callbacks by concern) | 3 files (handlers combined in one file) |

### Shared (matched across platforms)

| Feature | Status |
|---------|--------|
| Scanner | Both: Scanner, AdvertisementParser, ScannerFactory |
| Peripheral | Both: Peripheral, Connection, GattHandler, Internal, L2cap, GattStatusMapper, PeripheralFactory |
| Server | Both: GattServer, Advertiser, ExtendedAdvertiser, GattServerFactory |
| L2CAP | Both: Channel, Listener, ListenerFactory |
| Adapter | Both: BluetoothAdapter, AdapterFactory |
| Permissions | Both: BlePermissions |
| State restoration | Both: StateRestorationApi |
| LE Audio (ISO) | Both: IsochronousListenerFactory (stub on both platforms) |
| Cache | Both: GattCache (new in #279) |
| Observation persistence | Both: ObservationPersistence |

## Recommendations

1. **Implement iOS QuirkRegistry (#259)** - highest-impact parity gap. Android has full device quirk detection; iOS users cannot apply workarounds for known-broken peripherals.

2. **Decompose IosGattServerHandlers** (215 lines) into focused handler files matching Android's decomposition pattern. Not a feature gap but improves maintainability.

3. **Monitor AndroidGattServer file count** - 11 files is the result of decomposition. iOS has 3 files for the same feature set. This is legitimate asymmetry (Android Binder callbacks naturally decompose by callback type; iOS delegates are more monolithic). No action needed.

4. **BondManager asymmetry** is by design -- CoreBluetooth does not expose programmatic bond management. Document this in platform-specific docs (#256, #257).

5. **PairingRequestHandler asymmetry** is by design -- iOS handles pairing through the OS-level pairing dialog. Android requires app-level PIN/passkey handling. Document in platform guides.
