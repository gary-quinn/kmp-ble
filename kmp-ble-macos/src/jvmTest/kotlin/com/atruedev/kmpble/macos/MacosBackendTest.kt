package com.atruedev.kmpble.macos

import app.cash.turbine.test
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.backend.AdvertisingSetSpec
import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.GattServerEvent
import com.atruedev.kmpble.backend.ScanRequest
import com.atruedev.kmpble.backend.ServerCharacteristicSpec
import com.atruedev.kmpble.backend.ServerServiceSpec
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.macos.internal.AdvertisingData
import com.atruedev.kmpble.macos.internal.CbManagerState
import com.atruedev.kmpble.macos.internal.MacosAdapterTransport
import com.atruedev.kmpble.macos.internal.MacosAdvertiserTransport
import com.atruedev.kmpble.macos.internal.MacosEvent
import com.atruedev.kmpble.macos.internal.MacosEventKind
import com.atruedev.kmpble.macos.internal.MacosGattServerTransport
import com.atruedev.kmpble.macos.internal.MacosL2capListenerTransport
import com.atruedev.kmpble.macos.internal.MacosPermissions
import com.atruedev.kmpble.macos.internal.MacosScanTransport
import com.atruedev.kmpble.macos.internal.MacosStatus
import com.atruedev.kmpble.macos.internal.cbPermissions
import com.atruedev.kmpble.macos.internal.cbProperties
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.scanner.ScanFailedException
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.server.ServerCharacteristic
import com.atruedev.kmpble.server.ServerException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MacosBackendTest {
    private val heartRate = uuidFrom("180D")
    private val custom = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    @Test
    fun serviceLoaderFindsMacosBackend() {
        val backend = BleBackends.available().single { it.id == Macos.BACKEND_ID }
        assertIs<MacosBackend>(backend)
        val libraryAvailable =
            Macos.isNativeLibraryBundled() || System.getProperty(Macos.LIBRARY_PATH_PROPERTY) != null
        assertEquals(Macos.isMacosArm64() && libraryAvailable, backend.isSupported())
    }

    @Test
    fun advertisingDataParsesReconstructedStructures() {
        val bytes =
            byteArrayOf(0x05, 0x09, 'k'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), '!'.code.toByte()) +
                byteArrayOf(0x02, 0x0A, 0xF4.toByte()) +
                byteArrayOf(0x03, 0x03, 0x0D, 0x18) +
                byteArrayOf(0x11, 0x07) + custom.toByteArray().reversedArray() +
                byteArrayOf(0x04, 0x16, 0x0D, 0x18, 0x55) +
                byteArrayOf(0x05, 0xFF.toByte(), 0x4C, 0x00, 0x02, 0x15)
        val parsed = AdvertisingData.parse(bytes)
        assertEquals("kmp!", parsed.localName)
        assertEquals(-12, parsed.txPower)
        assertEquals(listOf(heartRate, custom), parsed.serviceUuids)
        assertContentEquals(byteArrayOf(0x55), parsed.serviceData.getValue(heartRate))
        assertContentEquals(byteArrayOf(0x02, 0x15), parsed.manufacturerData.getValue(0x004C))
        assertEquals(null, AdvertisingData.parse(byteArrayOf(0x09, 0x09, 0x01)).localName)
    }

    @Test
    fun eventsDecodeFromPlainNativePayloads() {
        val discovered =
            assertIs<MacosEvent.Discovered>(
                MacosEvent.decode(
                    MacosEventKind.DISCOVERED,
                    7,
                    1,
                    -55,
                    "UUID-1\nSensor",
                    byteArrayOf(0x02, 0x01, 0x06),
                ),
            )
        assertEquals("UUID-1", discovered.identifier)
        assertEquals("Sensor", discovered.peripheralName)
        assertEquals(true, discovered.connectable)
        assertEquals(-55, discovered.rssi)
        assertNull(
            assertIs<MacosEvent.Discovered>(
                MacosEvent.decode(MacosEventKind.DISCOVERED, 7, -1, -55, "UUID-1\n", null),
            ).connectable,
        )

        val characteristics =
            assertIs<MacosEvent.CharacteristicsDiscovered>(
                MacosEvent.decode(MacosEventKind.CHARACTERISTICS_DISCOVERED, 1, 2, 0, "3|2A37|18\n4|2A39|12", null),
            )
        assertEquals(listOf(3L to 18, 4L to 12), characteristics.characteristics.map { it.handle to it.properties })

        val batch = ByteBuffer.allocate(16 + 2 + 16 + 1)
        batch
            .putLong(9)
            .putInt(0)
            .putInt(2)
            .put(byteArrayOf(1, 2))
            .putLong(9)
            .putInt(2)
            .putInt(1)
            .put(3)
        val writes =
            assertIs<MacosEvent.PmWriteRequests>(
                MacosEvent.decode(MacosEventKind.PM_WRITE_REQUESTS, 5, 2, 0, "C1", batch.array()),
            )
        assertEquals(listOf(0, 2), writes.writes.map { it.offset })
        assertContentEquals(byteArrayOf(3), writes.writes.last().value)
        assertNull(MacosEvent.decode(999, 0, 0, 0, null, null))
    }

    @Test
    fun statusCodesMapToCommonErrors() {
        assertEquals(GattStatus.ReadNotPermitted, MacosStatus.gattStatus(0x02))
        assertEquals(GattStatus.InsufficientEncryption, MacosStatus.gattStatus(0x0F))
        assertEquals(GattStatus.Failure, MacosStatus.gattStatus(1000))
        assertTrue(MacosStatus.isLinkLoss(1007))
        assertEquals(0x0D, MacosStatus.attResult(GattStatus.InvalidAttributeLength))
        assertEquals(null, MacosStatus.disconnectError(0, null))
    }

    @Test
    fun scanDeliversRecordsAndRefCountsTheNativeScan() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack()
            val scan = MacosScanTransport(stack, settleTimeout = 1.seconds)
            val request = ScanRequest(emptyList(), legacyOnly = false)

            scan.scan(request).test {
                withTimeout(2.seconds) { while ("scanStart" !in api.calls) delay(5) }
                api.emitNow(
                    MacosEvent.Discovered(10, "UUID-2", "Fallback", -48, true, byteArrayOf(0x03, 0x03, 0x0D, 0x18)),
                )
                val record = awaitItem()
                assertEquals(Identifier("UUID-2"), record.identifier)
                assertEquals("UUID-2", record.handle)
                assertEquals("Fallback", record.name)
                assertEquals(listOf(heartRate), record.serviceUuids)

                api.emitNow(MacosEvent.CentralState(CbManagerState.POWERED_OFF))
                assertEquals(Macos.ERROR_ADAPTER_OFF, assertIs<ScanFailedException>(awaitError()).errorCode)
            }
            assertEquals(1, api.calls.count { it == "scanStart" })
            assertEquals(1, api.calls.count { it == "scanStop" })
        }

    @Test
    fun scanFailsWhenCoreBluetoothIsUnavailable() =
        runBlocking<Unit> {
            val (stack, _) = FakeMacosNativeApi.stack { initialCentralState = CbManagerState.UNAUTHORIZED }
            val error =
                assertFailsWith<ScanFailedException> {
                    MacosScanTransport(stack, 1.seconds).scan(ScanRequest(emptyList(), true)).first()
                }
            assertEquals(Macos.ERROR_UNAUTHORIZED, error.errorCode)

            val (missing, missingApi) = FakeMacosNativeApi.stack { missingUsageHost = HOST_APP }
            val usage =
                assertFailsWith<ScanFailedException> {
                    MacosScanTransport(missing, 1.seconds).scan(ScanRequest(emptyList(), true)).first()
                }
            assertEquals(Macos.ERROR_USAGE_DESCRIPTION_MISSING, usage.errorCode)
            assertTrue(HOST_APP in usage.message.orEmpty())
            assertTrue("centralStart" !in missingApi.calls)
        }

    @Test
    fun usageDescriptionCheckCanBeDisabled() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack { missingUsageHost = HOST_APP }
            System.setProperty(Macos.USAGE_DESCRIPTION_CHECK_PROPERTY, "false")
            try {
                assertEquals(CbManagerState.POWERED_ON, stack.awaitCentralSettled(1.seconds))
                assertTrue("centralStart" in api.calls)
                assertEquals(PermissionResult.Granted, MacosPermissions.check(stack))
            } finally {
                System.clearProperty(Macos.USAGE_DESCRIPTION_CHECK_PROPERTY)
            }
        }

    @Test
    fun adapterAndPermissionsFollowCoreBluetooth() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack()
            val adapter = MacosAdapterTransport(stack)
            withTimeout(2.seconds) { adapter.state.first { it == BluetoothAdapterState.On } }
            api.emitNow(MacosEvent.CentralState(CbManagerState.POWERED_OFF))
            withTimeout(2.seconds) { adapter.state.first { it == BluetoothAdapterState.Off } }
            assertTrue(adapter.bondedDevices().isEmpty())
            adapter.close()

            assertEquals(PermissionResult.Granted, MacosPermissions.check(stack))
            api.authorizationValue = 2
            assertEquals(PermissionResult.PermanentlyDenied(listOf("bluetooth")), MacosPermissions.check(stack))
            api.authorizationValue = 0
            assertIs<PermissionResult.Denied>(MacosPermissions.check(stack))
            api.missingUsageHost = HOST_APP
            assertEquals(
                PermissionResult.PermanentlyDenied(listOf("NSBluetoothAlwaysUsageDescription")),
                MacosPermissions.check(stack),
            )
        }

    @Test
    fun gattServerPublishesServicesAndRoutesRequests() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack { updateValueResults = ArrayDeque(listOf(0, 1)) }
            val server = MacosGattServerTransport(stack, settleTimeout = 1.seconds, notifyReadyTimeout = 1.seconds)
            val events = CopyOnWriteArrayList<GattServerEvent>()
            server.setEventListener { events += it }
            val spec =
                ServerCharacteristicSpec(
                    uuid = custom,
                    properties = ServerCharacteristic.Properties(read = true, write = true, notify = true),
                    permissions = ServerCharacteristic.Permissions(read = true, writeEncrypted = true),
                    descriptors = emptyList(),
                )
            assertEquals(0x1A, spec.cbProperties())
            assertEquals(0x09, spec.cbPermissions())
            server.open(listOf(ServerServiceSpec(heartRate, listOf(spec))))
            val characteristicHandle = 501L

            api.emitNow(
                MacosEvent.PmReadRequest(
                    request = 77,
                    characteristic = characteristicHandle,
                    offset = 1,
                    central = "C-1",
                ),
            )
            val read = assertIs<GattServerEvent.ReadRequest>(events.single())
            assertEquals(Identifier("C-1"), read.device)
            assertEquals(custom, read.characteristic)
            server.respond(77, GattStatus.Success, byteArrayOf(5))
            assertEquals(
                Triple(77L, 0, byteArrayOf(5).toList()),
                api.responses.single().let {
                    Triple(it.first, it.second, it.third!!.toList())
                },
            )

            api.emitNow(
                MacosEvent.PmWriteRequests(
                    78,
                    "C-1",
                    listOf(
                        FakeMacosNativeApi.write(characteristicHandle, 0, byteArrayOf(1)),
                        FakeMacosNativeApi.write(characteristicHandle, 1, byteArrayOf(2)),
                    ),
                ),
            )
            val write = assertIs<GattServerEvent.WriteRequest>(events.last())
            assertEquals(listOf(0, 1), write.writes.map { it.offset })
            api.emitNow(MacosEvent.PmReadRequest(request = 79, characteristic = 999, offset = 0, central = "C-1"))
            assertEquals(2, events.size)

            val notify = launch { server.notify(custom, Identifier("C-1"), byteArrayOf(9), indicate = false) }
            withTimeout(2.seconds) { while (api.updates.isEmpty()) delay(5) }
            api.emitNow(MacosEvent.PmReadyToUpdate)
            withTimeout(2.seconds) { notify.join() }
            assertEquals(2, api.updates.size)
            assertFailsWith<ServerException.NotifyFailed> {
                server.notify(
                    heartRate,
                    null,
                    byteArrayOf(1),
                    indicate = false,
                )
            }
            server.close()
            assertTrue("pmRemoveService" in api.calls)
        }

    @Test
    fun gattServerRequiresPoweredOnPeripheralManager() =
        runBlocking<Unit> {
            val (stack, _) = FakeMacosNativeApi.stack { initialPmState = CbManagerState.POWERED_OFF }
            assertFailsWith<ServerException.OpenFailed> {
                MacosGattServerTransport(stack, settleTimeout = 1.seconds).open(emptyList())
            }
        }

    @Test
    fun advertiserOwnsTheSingleCoreBluetoothSet() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack()
            val first = MacosAdvertiserTransport(stack, settleTimeout = 1.seconds, startTimeout = 1.seconds)
            val second = MacosAdvertiserTransport(stack, settleTimeout = 1.seconds, startTimeout = 1.seconds)
            val spec =
                AdvertisingSetSpec(
                    name = "kmp",
                    serviceUuids = listOf(heartRate),
                    manufacturerData = emptyMap(),
                    serviceData = emptyMap(),
                    connectable = true,
                    scannable = true,
                    includeTxPower = false,
                    mode = AdvertiseMode.Balanced,
                    txPower = AdvertiseTxPower.Medium,
                    legacy = true,
                    primaryPhy = Phy.Le1M,
                    secondaryPhy = null,
                )
            first.start(0, spec)
            assertEquals("kmp" to listOf(heartRate.toString()), api.advertising.single())
            assertFailsWith<AdvertiserException.StartFailed> { second.start(0, spec) }
            first.stop(0)
            assertTrue("pmStopAdvertising" in api.calls)
            second.start(0, spec)
            second.close()
            assertEquals(1, first.maxAdvertisingSets)
        }

    @Test
    fun l2capListenerPublishesAndAcceptsChannels() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack()
            val listener = MacosL2capListenerTransport(stack, settleTimeout = 1.seconds)
            val accepted = CopyOnWriteArrayList<com.atruedev.kmpble.backend.L2capStream>()
            listener.setAcceptListener { accepted += it }
            assertEquals(0x81, withTimeout(2.seconds) { listener.publish(secure = true) })

            api.emitNow(MacosEvent.PmL2capOpened(psm = 0x81, channel = 60, status = 0, message = null))
            api.emitNow(MacosEvent.PmL2capOpened(psm = 0x90, channel = 61, status = 0, message = null))
            assertEquals(listOf(0x81), accepted.map { it.psm })
            listener.close()
            assertTrue("pmUnpublish:${0x81}" in api.calls)
            delay(10.milliseconds)
        }

    private companion object {
        const val HOST_APP = "/Applications/Host.app"
    }
}
