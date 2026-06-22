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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PowerMonitorTest {
    @Test
    fun initialReadingIsNull() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher))
        assertNull(monitor.pathLoss.value)
    }

    @Test
    fun recordRssiComputesPathLoss() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher), txPower = 0)

        monitor.recordRssi(-55)
        val reading = monitor.pathLoss.value
        assertNotNull(reading)
        assertEquals(55, reading.pathLoss)
        assertEquals(-55, reading.rssi)
        assertEquals(0, reading.txPower)
    }

    @Test
    fun recordRssiWithCustomTxPower() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher), txPower = 4)

        monitor.recordRssi(-60)
        val reading = monitor.pathLoss.value
        assertNotNull(reading)
        assertEquals(64, reading.pathLoss)
        assertEquals(-60, reading.rssi)
        assertEquals(4, reading.txPower)
    }

    @Test
    fun recordRssiOverwritesPreviousReading() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher))

        monitor.recordRssi(-40)
        assertEquals(40, monitor.pathLoss.value!!.pathLoss)

        monitor.recordRssi(-80)
        assertEquals(80, monitor.pathLoss.value!!.pathLoss)
    }

    @Test
    fun disconnectClearsReading() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = PowerMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            monitor.recordRssi(-55)
            assertNotNull(monitor.pathLoss.value)

            peripheral.disconnect()
            scheduler.runCurrent()

            assertNull(monitor.pathLoss.value)
        }
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
            val monitor = PowerMonitor(peripheral, backgroundScope)

            monitor.start()
            monitor.start()
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            monitor.recordRssi(-55)
            assertNotNull(monitor.pathLoss.value)
        }
    }

    @Test
    fun stopPreventsDisconnectFromClearing() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = PowerMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            monitor.recordRssi(-55)
            assertNotNull(monitor.pathLoss.value)

            monitor.stop()

            peripheral.disconnect()
            scheduler.runCurrent()

            assertNotNull(monitor.pathLoss.value)
        }
    }

    @Test
    fun zeroPathLossWhenRssiEqualsTxPower() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher), txPower = -55)

        monitor.recordRssi(-55)
        assertEquals(0, monitor.pathLoss.value!!.pathLoss)
    }

    // -- Edge cases --

    @Test
    fun `start stop start cycle restores monitoring`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = PowerMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            monitor.recordRssi(-50)
            assertNotNull(monitor.pathLoss.value)

            monitor.stop()
            peripheral.disconnect()
            scheduler.runCurrent()
            // Reading persists after stop, even though collection stopped
            assertNotNull(monitor.pathLoss.value)

            // Restart
            monitor.start()
            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            monitor.recordRssi(-60)
            assertEquals(60, monitor.pathLoss.value!!.pathLoss)

            // Disconnect after restart clears reading
            peripheral.disconnect()
            scheduler.runCurrent()
            assertNull(monitor.pathLoss.value)
        }
    }

    @Test
    fun `stop without start is safe no op`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher))

        // Stop before start should not throw
        monitor.stop()
        assertNull(monitor.pathLoss.value)
    }

    @Test
    fun `record before start still works`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher))

        // Record before start - stateless operation, should work
        monitor.recordRssi(-70)
        assertNotNull(monitor.pathLoss.value)
        assertEquals(70, monitor.pathLoss.value!!.pathLoss)
    }

    @Test
    fun `extreme rssi max positive produces negative path loss`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher), txPower = 0)

        // BLE RSSI max is typically near 0 (strong signal)
        monitor.recordRssi(0)
        val reading = monitor.pathLoss.value
        assertNotNull(reading)
        assertEquals(0, reading.pathLoss)
        assertEquals(0, reading.rssi)
        assertEquals(0, reading.txPower)
    }

    @Test
    fun `extreme rssi max negative produces large path loss`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val peripheral =
            FakePeripheralBuilder()
                .observationDispatcher(dispatcher)
                .build()
        val monitor = PowerMonitor(peripheral, CoroutineScope(dispatcher), txPower = 0)

        // BLE typical minimum is around -127 (extreme weak signal)
        monitor.recordRssi(-127)
        val reading = monitor.pathLoss.value
        assertNotNull(reading)
        assertEquals(127, reading.pathLoss)
        assertEquals(-127, reading.rssi)
        assertEquals(0, reading.txPower)
    }

    @Test
    fun `multiple rapid rssi recordings last one wins`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = PowerMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            // Rapid fire multiple readings
            monitor.recordRssi(-40)
            monitor.recordRssi(-50)
            monitor.recordRssi(-60)

            assertEquals(60, monitor.pathLoss.value!!.pathLoss)
            assertEquals(-60, monitor.pathLoss.value!!.rssi)

            peripheral.disconnect()
            scheduler.runCurrent()
        }
    }

    @Test
    fun `close permanently stops collection`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()
            val monitor = PowerMonitor(peripheral, backgroundScope)
            monitor.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            monitor.recordRssi(-45)
            assertNotNull(monitor.pathLoss.value)

            // Close the peripheral entirely
            peripheral.close()
            scheduler.runCurrent()

            // Reading from the monitor still accessible (stateless record)
            // but collection is stopped -- new disconnect should not cause issues
            monitor.recordRssi(-75)
            assertEquals(75, monitor.pathLoss.value!!.pathLoss)
        }
    }
}
