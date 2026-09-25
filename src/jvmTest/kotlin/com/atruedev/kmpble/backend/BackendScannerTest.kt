package com.atruedev.kmpble.backend

import app.cash.turbine.test
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.internal.serviceUuidHint
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.RawAdvertising
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanPredicate
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
class BackendScannerTest {
    private val heartRate = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val battery = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")

    @AfterTest
    fun tearDown() {
        PeripheralRegistry.clear()
    }

    @Test
    fun recordsBecomeAdvertisementsThatConnectThroughTheSameBackend() =
        runBlocking<Unit> {
            val backend = FakeBackend()
            val scanner = backend.newScanner { emission = EmissionPolicy.All }

            scanner.scanEvents.test {
                backend.scan.records.send(record("AA:BB:CC:DD:EE:01", "/dev/1", serviceUuids = listOf(heartRate)))
                val found = assertIs<ScanEvent.Found>(awaitItem()).advertisement
                assertEquals(Identifier("AA:BB:CC:DD:EE:01"), found.identifier)
                assertEquals(listOf(heartRate), found.serviceUuids)
                assertContentEquals(byteArrayOf(1, 2), found.manufacturerData.getValue(0x004C).toByteArray())
                assertIs<RawAdvertising.Reconstructed>(found.rawAdvertising)
                assertEquals("/dev/1", found.backendHandle(backend))
                assertEquals(null, found.backendHandle(FakeBackend(id = "other")))

                val peripheral = found.toPeripheral()
                assertEquals(listOf(Identifier("AA:BB:CC:DD:EE:01") to "/dev/1"), backend.createdPeripherals.toList())
                peripheral.close()
                cancelAndIgnoreRemainingEvents()
            }
            scanner.close()
        }

    @Test
    fun filtersRunInCoreAndServiceHintReachesTransport() =
        runBlocking<Unit> {
            val backend = FakeBackend()
            val scanner =
                backend.newScanner {
                    emission = EmissionPolicy.All
                    filters { match { serviceUuid(heartRate) } }
                }

            scanner.scanEvents.test {
                backend.scan.records.send(record("AA:BB:CC:DD:EE:02", "/dev/2", serviceUuids = listOf(battery)))
                backend.scan.records.send(record("AA:BB:CC:DD:EE:03", "/dev/3", serviceUuids = listOf(heartRate)))
                val found = assertIs<ScanEvent.Found>(awaitItem()).advertisement
                assertEquals(Identifier("AA:BB:CC:DD:EE:03"), found.identifier)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(heartRate),
                backend.scan.requests
                    .single()
                    .serviceUuids,
            )
            scanner.close()
        }

    @Test
    fun serviceHintIsEmptyWhenAnyGroupAcceptsAllServices() {
        assertTrue(serviceUuidHint(emptyList()).isEmpty())
        assertTrue(
            serviceUuidHint(
                listOf(listOf(ScanPredicate.ServiceUuid(heartRate)), listOf(ScanPredicate.NamePrefix("x"))),
            ).isEmpty(),
        )
        assertEquals(
            listOf(heartRate, battery),
            serviceUuidHint(
                listOf(listOf(ScanPredicate.ServiceUuid(heartRate)), listOf(ScanPredicate.ServiceUuid(battery))),
            ),
        )
    }

    private fun record(
        address: String,
        handle: String,
        serviceUuids: List<Uuid>,
    ): ScanRecord =
        ScanRecord(
            handle = handle,
            identifier = Identifier(address),
            name = "dev",
            rssi = -50,
            txPower = null,
            isConnectable = true,
            serviceUuids = serviceUuids,
            manufacturerData = mapOf(0x004C to byteArrayOf(1, 2)),
            serviceData = emptyMap(),
            rawAdvertising = byteArrayOf(0x02, 0x01, 0x06),
        )
}
