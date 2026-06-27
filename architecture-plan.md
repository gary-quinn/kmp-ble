# Issue #441: Decompose Peripheral.kt (373 lines) into focused handler modules

## Scope
Decompose Peripheral.kt (373 lines) into focused modules by responsibility:
- **PeripheralConnection.kt** - connect/disconnect/close/bond/lifecycle (lines 37-48, 119-211)
- **PeripheralGatt.kt** - GATT operations (read/write/observe/descriptors/MTU) (lines 66-180)
- **PeripheralInfo.kt** - state discovery/info properties (services/findCharacteristics/findDescriptors/rssi/phy/DLE) (lines 51-65, 247-372)
- **PeripheralExtensions.kt** - convenience extensions (dump/whenReady/connectAndDiscover) (already extracted)
- **PeripheralFactory.kt** - Advertisement.toPeripheral() (already extracted)
- **PeripheralDump.kt** - dump() and related helpers (from PeripheralExtensions.kt)

## Files to touch
- `src/commonMain/kotlin/com/atruedev/kmpble/peripheral/Peripheral.kt` - split into modules
- `src/commonMain/kotlin/com/atruedev/kmpble/peripheral/PeripheralConnection.kt` (new)
- `src/commonMain/kotlin/com/atruedev/kmpble/peripheral/PeripheralGatt.kt` (new)
- `src/commonMain/kotlin/com/atruedev/kmpble/peripheral/PeripheralInfo.kt` (new)
- `src/commonMain/kotlin/com/atruedev/kmpble/peripheral/PeripheralDump.kt` (new)

## Risks
- API surface remains identical - no breaking changes
- Must verify all implementations (AndroidPeripheral, IosPeripheral) still compile after split
- Test coverage must remain - existing tests should still pass

## Implementation Strategy
1. Create PeripheralConnection.kt with connection/bond methods
2. Create PeripheralGatt.kt with GATT operations
3. Create PeripheralInfo.kt with discovery/info properties
4. Create PeripheralDump.kt with dump() implementation
5. Delete duplicate content from Peripheral.kt (keep imports/empty interface)
6. Verify compilation: `./gradlew compileKotlinJvm`
7. Run tests: `./gradlew allTests`
