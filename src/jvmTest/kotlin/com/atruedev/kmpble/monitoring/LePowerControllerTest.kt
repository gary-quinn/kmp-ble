package com.atruedev.kmpble.monitoring

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalBleApi::class, ExperimentalCoroutinesApi::class)
class LePowerControllerTest {
    private val defaultResult =
        ConnectionParameterUpdateResult(
            negotiatedInterval = 12.5.milliseconds,
            negotiatedLatency = 0,
            negotiatedSupervisionTimeout = 500.milliseconds,
        )

    @Test
    fun requestPeerPowerChangeReturnsAcceptedTrueWhenHandlerReturnsResult() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { defaultResult }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            val response = controller.requestPeerPowerChange(-4)
            assertTrue(response.accepted)
            assertEquals(-4, response.targetDbm)
            assertEquals(12.5.milliseconds, response.negotiatedInterval)
            assertEquals(0, response.negotiatedLatency)
            assertEquals(500.milliseconds, response.negotiatedSupervisionTimeout)

            controller.stop()
        }
    }

    @Test
    fun requestPeerPowerChangeReturnsDefaultResultWhenNoHandler() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            val response = controller.requestPeerPowerChange(-4)
            assertTrue(response.accepted)
            assertEquals(-4, response.targetDbm)
            assertNotNull(response.negotiatedInterval)
            assertEquals(0, response.negotiatedLatency)
            assertEquals(500.milliseconds, response.negotiatedSupervisionTimeout)

            controller.stop()
        }
    }

    @Test
    fun requestPeerPowerChangeUsesHighPowerParamsForDbmGeMinus4() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            var capturedParams: ConnectionParameters? = null
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { params ->
                            capturedParams = params
                            defaultResult
                        }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            controller.requestPeerPowerChange(-4)
            assertNotNull(capturedParams)
            assertEquals(0, capturedParams.slaveLatency)
            assertEquals(500.milliseconds, capturedParams.supervisionTimeout)

            controller.stop()
        }
    }

    @Test
    fun requestPeerPowerChangeUsesBalancedParamsForDbmGeMinus12() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            var capturedParams: ConnectionParameters? = null
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { params ->
                            capturedParams = params
                            defaultResult
                        }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            controller.requestPeerPowerChange(-10)
            assertNotNull(capturedParams)
            assertEquals(2, capturedParams.slaveLatency)
            assertEquals(2000.milliseconds, capturedParams.supervisionTimeout)

            controller.stop()
        }
    }

    @Test
    fun requestPeerPowerChangeUsesLowPowerParamsForDbmGeMinus20() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            var capturedParams: ConnectionParameters? = null
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { params ->
                            capturedParams = params
                            defaultResult
                        }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            controller.requestPeerPowerChange(-15)
            assertNotNull(capturedParams)
            assertEquals(4, capturedParams.slaveLatency)
            assertEquals(4000.milliseconds, capturedParams.supervisionTimeout)

            controller.stop()
        }
    }

    @Test
    fun requestPeerPowerChangeUsesMaxSavingParamsForDbmLtMinus20() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            var capturedParams: ConnectionParameters? = null
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { params ->
                            capturedParams = params
                            defaultResult
                        }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            controller.requestPeerPowerChange(-25)
            assertNotNull(capturedParams)
            assertEquals(7, capturedParams.slaveLatency)
            assertEquals(6000.milliseconds, capturedParams.supervisionTimeout)

            controller.stop()
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
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { defaultResult }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()
            controller.start()
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            val response = controller.requestPeerPowerChange(-4)
            assertTrue(response.accepted)

            controller.stop()
        }
    }

    @Test
    fun incomingPowerRequestsIsEmptyByDefault() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            val received = mutableListOf<PeerPowerRequest>()
            val job =
                backgroundScope.launch {
                    controller.incomingPowerRequests.collect { received.add(it) }
                }
            scheduler.runCurrent()

            assertTrue(received.isEmpty())
            job.cancel()

            controller.stop()
        }
    }

    @Test
    fun stopThenRequestReturnsNoHandler() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                        onConnectionParameterUpdate { defaultResult }
                    }.build()

            val controller = LePowerController(peripheral, backgroundScope)
            controller.start()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()

            controller.stop()

            // After stop, requestPeerPowerChange still works - it's stateless
            val response = controller.requestPeerPowerChange(-4)
            assertTrue(response.accepted)
        }
    }
}
