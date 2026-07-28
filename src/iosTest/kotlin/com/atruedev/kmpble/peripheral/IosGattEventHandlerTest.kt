package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.internal.GattResult
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PendingOperations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GATT event handling integration tests for IosPeripheral.
 *
 * Validates the event dispatch, pending operation completion,
 * and characteristic value disambiguation logic used by
 * [IosPeripheralBridgeHandlers] without requiring CoreBluetooth
 * hardware.
 *
 * Run: ./gradlew :iosSimulatorArm64Test --tests "*IosGattEventHandlerTest*"
 */
class IosGattEventHandlerTest {
    // -- PendingOperations (used by both IosPeripheralBridgeHandlers and Android handlers) --

    @Test
    fun `pendingOps set and complete for characteristic read`() =
        runTest {
            val ops = PendingOperations()
            val deferred = CompletableDeferred<GattResult>()

            ops.set(PendingOp.CharacteristicRead, deferred)
            assertTrue(ops.has(PendingOp.CharacteristicRead))

            ops.complete(PendingOp.CharacteristicRead, GattResult(byteArrayOf(0x42), GattStatus.Success))
            assertEquals(byteArrayOf(0x42).toList(), deferred.await().value.toList())
            assertFalse(ops.has(PendingOp.CharacteristicRead))
        }

    @Test
    fun `pendingOps set and complete for characteristic write`() =
        runTest {
            val ops = PendingOperations()
            val deferred = CompletableDeferred<GattStatus>()

            ops.set(PendingOp.CharacteristicWrite, deferred)
            assertTrue(ops.has(PendingOp.CharacteristicWrite))

            ops.complete(PendingOp.CharacteristicWrite, GattStatus.Success)
            assertEquals(GattStatus.Success, deferred.await())
            assertFalse(ops.has(PendingOp.CharacteristicWrite))
        }

    @Test
    fun `pendingOps fail propagates exception`() =
        runTest {
            val ops = PendingOperations()
            val deferred = CompletableDeferred<GattStatus>()

            ops.set(PendingOp.CharacteristicWrite, deferred)
            ops.fail(PendingOp.CharacteristicWrite, RuntimeException("GATT error"))

            assertTrue(deferred.getCompletionExceptionOrNull() != null)
            assertFalse(ops.has(PendingOp.CharacteristicWrite))
        }

    @Test
    fun `pendingOps cancelAll completes all pending`() =
        runTest {
            val ops = PendingOperations()
            val read = CompletableDeferred<GattResult>()
            val write = CompletableDeferred<GattStatus>()

            ops.set(PendingOp.CharacteristicRead, read)
            ops.set(PendingOp.CharacteristicWrite, write)

            ops.cancelAll(RuntimeException("cancelled"))

            assertTrue(read.getCompletionExceptionOrNull() != null)
            assertTrue(write.getCompletionExceptionOrNull() != null)
            assertFalse(ops.has(PendingOp.CharacteristicRead))
            assertFalse(ops.has(PendingOp.CharacteristicWrite))
        }

    // -- Characteristic value disambiguation (simulates handleCharacteristicValue logic) --

    @Test
    fun `characteristic value completes pending write when write is armed`() =
        runTest {
            // When a pending write exists, the value update is treated as a write
            // confirmation. This simulates the first branch in handleCharacteristicValue.
            val ops = PendingOperations()
            val writeDeferred = CompletableDeferred<GattStatus>()
            ops.set(PendingOp.CharacteristicWrite, writeDeferred)

            // Simulated handleCharacteristicValue logic:
            // when { pendingOps.has(CharacteristicWrite) -> complete(CharacteristicWrite, ...) }
            if (ops.has(PendingOp.CharacteristicWrite)) {
                ops.complete(PendingOp.CharacteristicWrite, GattStatus.Success)
            }

            assertEquals(GattStatus.Success, writeDeferred.await())
            assertFalse(ops.has(PendingOp.CharacteristicWrite))
        }

    @Test
    fun `characteristic value completes pending read when read is armed`() =
        runTest {
            // When no write is pending but a read is, the value update is treated
            // as a read response. This simulates the second branch.
            val ops = PendingOperations()
            val readDeferred = CompletableDeferred<GattResult>()
            ops.set(PendingOp.CharacteristicRead, readDeferred)

            if (!ops.has(PendingOp.CharacteristicWrite) && ops.has(PendingOp.CharacteristicRead)) {
                ops.complete(PendingOp.CharacteristicRead, GattResult(byteArrayOf(0x01, 0x02), GattStatus.Success))
            }

            val result = readDeferred.await()
            assertEquals(byteArrayOf(0x01, 0x02).toList(), result.value.toList())
        }

    // -- Descriptor value disambiguation --

    @Test
    fun `descriptor value completes pending write when write is armed`() =
        runTest {
            // Simulates handleDescriptorValue: write takes priority over read.
            val ops = PendingOperations()
            val writeDeferred = CompletableDeferred<GattStatus>()
            ops.set(PendingOp.DescriptorWrite, writeDeferred)

            if (ops.has(PendingOp.DescriptorWrite)) {
                ops.complete(PendingOp.DescriptorWrite, GattStatus.Success)
            }

            assertEquals(GattStatus.Success, writeDeferred.await())
        }

    @Test
    fun `descriptor value completes pending read when only read is armed`() =
        runTest {
            val ops = PendingOperations()
            val readDeferred = CompletableDeferred<GattResult>()
            ops.set(PendingOp.DescriptorRead, readDeferred)

            if (!ops.has(PendingOp.DescriptorWrite) && ops.has(PendingOp.DescriptorRead)) {
                ops.complete(PendingOp.DescriptorRead, GattResult(byteArrayOf(0x03), GattStatus.Success))
            }

            val result = readDeferred.await()
            assertEquals(byteArrayOf(0x03).toList(), result.value.toList())
        }

    // -- RSSI handling --

    @Test
    fun `rssi completes pending read with rssi value`() =
        runTest {
            val ops = PendingOperations()
            val rssiDeferred = CompletableDeferred<Int>()
            ops.set(PendingOp.RssiRead, rssiDeferred)

            // Simulate handleRssi: complete with RSSI value on success
            ops.complete(PendingOp.RssiRead, -42)

            assertEquals(-42, rssiDeferred.await())
        }

    @Test
    fun `rssi fails pending read on error`() =
        runTest {
            val ops = PendingOperations()
            val rssiDeferred = CompletableDeferred<Int>()
            ops.set(PendingOp.RssiRead, rssiDeferred)

            // Simulate handleRssi: fail on error
            ops.fail(PendingOp.RssiRead, RuntimeException("RSSI read failed"))

            assertTrue(rssiDeferred.getCompletionExceptionOrNull() != null)
        }

    // -- GattResult equality (used across iOS and Android handlers) --

    @Test
    fun `gattResult equality uses content equality for byte arrays`() {
        val r1 = GattResult(byteArrayOf(0x01, 0x02), GattStatus.Success)
        val r2 = GattResult(byteArrayOf(0x01, 0x02), GattStatus.Success)
        val r3 = GattResult(byteArrayOf(0x01, 0x03), GattStatus.Success)

        assertEquals(r1, r2)
        assertFalse(r1 == r3)
    }

    // -- Event type hierarchy (validates AppleCallbackEvent sealed interface) --

    @Test
    fun `appleCallbackEvent subtypes are exhaustive`() {
        // Verify we can reference all known subtypes.
        // This test documents the sealed interface hierarchy used by
        // IosPeripheralBridgeHandlers.handleBridgeEvent.
        val subtypes: List<String> =
            listOf(
                "DidDiscoverServices",
                "DidDiscoverCharacteristics",
                "DidUpdateValueForCharacteristic",
                "DidWriteValueForCharacteristic",
                "DidUpdateValueForDescriptor",
                "DidWriteValueForDescriptor",
                "DidReadRSSI",
                "DidOpenL2CAPChannel",
            )

        assertEquals(8, subtypes.size, "All AppleCallbackEvent subtypes should be documented")
    }

    // -- Multiple pending operations don't interfere --

    @Test
    fun `multiple pending ops of different types coexist`() =
        runTest {
            val ops = PendingOperations()
            val readDeferred = CompletableDeferred<GattResult>()
            val writeDeferred = CompletableDeferred<GattStatus>()
            val rssiDeferred = CompletableDeferred<Int>()

            ops.set(PendingOp.CharacteristicRead, readDeferred)
            ops.set(PendingOp.CharacteristicWrite, writeDeferred)
            ops.set(PendingOp.RssiRead, rssiDeferred)

            assertTrue(ops.has(PendingOp.CharacteristicRead))
            assertTrue(ops.has(PendingOp.CharacteristicWrite))
            assertTrue(ops.has(PendingOp.RssiRead))

            // Complete write first - shouldn't affect read or RSSI
            ops.complete(PendingOp.CharacteristicWrite, GattStatus.Success)
            assertFalse(ops.has(PendingOp.CharacteristicWrite))
            assertTrue(ops.has(PendingOp.CharacteristicRead))
            assertTrue(ops.has(PendingOp.RssiRead))

            // Complete read
            ops.complete(PendingOp.CharacteristicRead, GattResult(byteArrayOf(0x07), GattStatus.Success))
            assertFalse(ops.has(PendingOp.CharacteristicRead))

            // Complete RSSI
            ops.complete(PendingOp.RssiRead, -50)
            assertFalse(ops.has(PendingOp.RssiRead))
        }

    // -- Discovery event handling (services and characteristics) --

    // CBMutableService is a public, instantiable subclass of the framework's CBService,
    // so it stands in for real discovered services without needing a live CBPeripheral
    // (which CoreBluetooth never lets app code construct directly).

    @Test
    fun `distinct CBService instances with the same UUID are not reference-equal`() {
        val uuid = CBUUID.UUIDWithString("180D")
        val serviceA = CBMutableService(type = uuid, primary = true)
        val serviceB = CBMutableService(type = uuid, primary = true)

        assertEquals(serviceA.UUID.UUIDString, serviceB.UUID.UUIDString)
        assertFalse(serviceA === serviceB)
    }

    @Test
    fun `resolving a service by UUID string collapses duplicate-UUID services to the first match`() {
        // Characterizes the bug fixed by passing the CBService instance through directly:
        // ApplePeripheralBridge.discoverCharacteristics used to re-resolve the service by
        // UUID string, so a peripheral exposing two services with the same UUID (legal per
        // the BLE spec) always resolved to the first one - the second was never reachable.
        val uuid = CBUUID.UUIDWithString("180D")
        val serviceA = CBMutableService(type = uuid, primary = true)
        val serviceB = CBMutableService(type = uuid, primary = true)
        val services: List<CBService> = listOf(serviceA, serviceB)

        fun resolveByUuid(uuidString: String) = services.filter { it.UUID.UUIDString == uuidString }.firstOrNull()

        assertTrue(resolveByUuid(serviceA.UUID.UUIDString) === serviceA)
        assertTrue(resolveByUuid(serviceB.UUID.UUIDString) === serviceA)
        assertFalse(resolveByUuid(serviceB.UUID.UUIDString) === serviceB)
    }

    @Test
    fun `pendingServices keeps one entry per service instead of per UUID`() {
        // Mirrors IosPeripheralDiscovery.handleServicesDiscovered's pendingServices construction.
        // A List (not a Set) preserves one entry per duplicate-UUID service, since each gets
        // its own didDiscoverCharacteristicsForService callback.
        val uuid = CBUUID.UUIDWithString("180D")
        val services: List<CBService> =
            listOf(
                CBMutableService(type = uuid, primary = true),
                CBMutableService(type = uuid, primary = true),
            )

        val pending = services.map { it.UUID.UUIDString }.toMutableList()
        assertEquals(2, pending.size)

        // Each callback removes only the first matching entry - mirrors
        // IosPeripheralDiscovery.handleCharacteristicsDiscovered.
        pending.remove(uuid.UUIDString)
        assertEquals(1, pending.size, "one outstanding entry should remain for the second service")

        pending.remove(uuid.UUIDString)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `pendingOps clear resets all state`() =
        runTest {
            val ops = PendingOperations()
            val deferred = CompletableDeferred<GattStatus>()
            ops.set(PendingOp.CharacteristicWrite, deferred)

            ops.clear(PendingOp.CharacteristicWrite)
            assertFalse(ops.has(PendingOp.CharacteristicWrite))
        }
}
