package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.logging.BleLogConfig
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.BleLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlueZScannerLifecycleTest {
    private val logEvents = mutableListOf<BleLogEvent>()

    @BeforeTest
    fun setUp() {
        logEvents.clear()
        BleLogConfig.logger = BleLogger { logEvents += it }
    }

    @AfterTest
    fun tearDown() {
        BleLogConfig.logger = null
    }

    @Test
    fun startDiscoveryAppliesLeFilterAndStartsScan() =
        runBlocking {
            val session = FakeBlueZAdapterSession()
            val factory = FakeBlueZSessionFactory(session)
            val scanner = BlueZScanner(sessionFactory = factory)

            val collectJob = launch { scanner.scanEvents.collect { } }
            session.awaitDiscoveryStarted()

            assertEquals(1, session.setDiscoveryFilterCalls)
            assertEquals("le", session.lastDiscoveryFilter?.get("Transport")?.value)
            assertEquals(true, session.lastDiscoveryFilter?.get("DuplicateData")?.value)
            assertEquals(1, session.registerHandlersCalls)
            assertEquals(1, session.seedExistingDevicesCalls)
            assertEquals(1, session.startDiscoveryCalls)
            assertEquals(0, session.stopDiscoveryCalls)

            collectJob.cancelAndJoin()
            session.awaitDiscoveryStopped()

            assertEquals(1, session.stopDiscoveryCalls)
            assertEquals(1, session.unregisterHandlersCalls)
            assertEquals(1, session.closeConnectionCalls)
            assertTrue(logEvents.any { it is BleLogEvent.ScanStarted })
            assertTrue(logEvents.any { it is BleLogEvent.ScanStopped })

            scanner.close()
        }

    @Test
    fun closeCancelsCollectorAndCleansUpSession() =
        runBlocking {
            val session = FakeBlueZAdapterSession()
            val factory = FakeBlueZSessionFactory(session)
            val scanner = BlueZScanner(sessionFactory = factory)

            val collectJob = launch { scanner.scanEvents.collect { } }
            session.awaitDiscoveryStarted()

            collectJob.cancelAndJoin()
            session.awaitDiscoveryStopped()

            assertEquals(1, session.stopDiscoveryCalls)
            assertEquals(1, session.unregisterHandlersCalls)
            assertEquals(1, session.closeConnectionCalls)
        }

    @Test
    fun setDiscoveryFilterFailureLogsWarningAndContinuesScan() =
        runBlocking {
            val session =
                FakeBlueZAdapterSession(
                    setDiscoveryFilterResult = Result.failure(IllegalStateException("filter rejected")),
                )
            val factory = FakeBlueZSessionFactory(session)
            val scanner = BlueZScanner(sessionFactory = factory)

            val collectJob = launch { scanner.scanEvents.collect { } }
            session.awaitDiscoveryStarted()

            assertEquals(1, session.setDiscoveryFilterCalls)
            assertEquals(1, session.startDiscoveryCalls)
            val warning = logEvents.filterIsInstance<BleLogEvent.Warning>().singleOrNull()
            assertNotNull(warning)
            assertTrue(warning.message.contains("SetDiscoveryFilter failed"))
            assertTrue(warning.message.contains("filter rejected"))

            collectJob.cancelAndJoin()
            scanner.close()
        }

    @Test
    fun startDiscoveryFailureUnregistersAndClosesWithoutStopDiscovery() =
        runBlocking {
            val session = FakeBlueZAdapterSession(startDiscoveryResult = false)
            val factory = FakeBlueZSessionFactory(session)
            val scanner = BlueZScanner(sessionFactory = factory)

            val event = scanner.scanEvents.first()
            assertTrue(event is ScanEvent.Failed)
            assertEquals(BlueZScanner.ERROR_DISCOVERY_FAILED, event.error.errorCode)

            assertEquals(1, session.startDiscoveryCalls)
            assertEquals(1, session.unregisterHandlersCalls)
            assertEquals(1, session.closeConnectionCalls)
            assertEquals(0, session.stopDiscoveryCalls)
        }

    @Test
    fun advertisingFlagsEqualityUsesContentNotReference() {
        val left =
            BlueZDeviceSnapshot(
                dbusPath = "/org/bluez/hci0/dev_AA",
                address = "AA:BB:CC:DD:EE:FF",
                name = null,
                rssi = -70,
                txPower = null,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
                serviceData = emptyMap(),
                advertisingFlags = byteArrayOf(0x06),
            )
        val right = left.copy(advertisingFlags = byteArrayOf(0x06))

        assertTrue(left == right)
        assertEquals(left.hashCode(), right.hashCode())
    }
}

private suspend fun Job.cancelAndJoin() {
    cancel()
    join()
}
