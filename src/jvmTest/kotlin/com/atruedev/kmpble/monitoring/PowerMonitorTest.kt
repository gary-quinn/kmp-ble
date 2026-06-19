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
}
