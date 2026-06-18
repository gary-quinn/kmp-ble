package com.atruedev.kmpble.monitoring

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionQualityMonitorTest {
    @Test
    fun initialQualityIsDefault() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = ConnectionQualityMonitor(peripheral, CoroutineScope(dispatcher))
        val quality = monitor.connectionQuality.value
        assertEquals(0, quality.totalConnections)
        assertEquals(0, quality.totalDisconnections)
        assertEquals(0, quality.reconnectionCount)
        assertNull(quality.lastRssi)
        assertFalse(quality.isConnected)
    }

    @Test
    fun connectingIncrementsConnectionCount() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            // connect() completes synchronously on real dispatchers;
            // process any pending test-dispatcher tasks to let the collector run
            scheduler.runCurrent()

            val quality = monitor.connectionQuality.value
            assertEquals(1, quality.totalConnections)
            assertTrue(quality.isConnected)
        }
    }

    @Test
    fun disconnectIncrementsDisconnectionCount() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            peripheral.disconnect()
            scheduler.runCurrent()

            val quality = monitor.connectionQuality.value
            assertEquals(1, quality.totalConnections)
            assertEquals(1, quality.totalDisconnections)
            assertFalse(quality.isConnected)
        }
    }

    @Test
    fun reconnectAfterDisconnectTracksBothEvents() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            peripheral.disconnect()
            scheduler.runCurrent()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            val quality = monitor.connectionQuality.value
            assertEquals(2, quality.totalConnections)
            assertEquals(1, quality.totalDisconnections)
            assertEquals(1, quality.reconnectionCount)
            assertTrue(quality.isConnected)
        }
    }

    @Test
    fun recordRssiUpdatesLastRssi() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = ConnectionQualityMonitor(peripheral, CoroutineScope(dispatcher))

        monitor.recordRssi(-55)
        assertEquals(-55, monitor.connectionQuality.value.lastRssi)

        monitor.recordRssi(-72)
        assertEquals(-72, monitor.connectionQuality.value.lastRssi)
    }

    @Test
    fun startIsIdempotent() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)

            monitor.start()
            monitor.start()
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            assertEquals(1, monitor.connectionQuality.value.totalConnections)
        }
    }

    @Test
    fun multipleConnectDisconnectCyclesAreTracked() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            peripheral.disconnect()
            scheduler.runCurrent()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            peripheral.disconnect()
            scheduler.runCurrent()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            val quality = monitor.connectionQuality.value
            assertEquals(3, quality.totalConnections)
            assertEquals(2, quality.totalDisconnections)
            assertEquals(2, quality.reconnectionCount)
            assertTrue(quality.isConnected)
        }
    }

    @Test
    fun rssiAndConnectionAreIndependent() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
            monitor.start()

            monitor.recordRssi(-80)
            assertEquals(-80, monitor.connectionQuality.value.lastRssi)
            assertFalse(monitor.connectionQuality.value.isConnected)

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            assertEquals(-80, monitor.connectionQuality.value.lastRssi)
            assertTrue(monitor.connectionQuality.value.isConnected)
        }
    }

    @Test
    fun stopPreventsFurtherUpdates() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            assertEquals(1, monitor.connectionQuality.value.totalConnections)

            monitor.stop()

            peripheral.disconnect()
            scheduler.runCurrent()
            assertEquals(1, monitor.connectionQuality.value.totalConnections)
            assertEquals(0, monitor.connectionQuality.value.totalDisconnections)
        }
    }
}
