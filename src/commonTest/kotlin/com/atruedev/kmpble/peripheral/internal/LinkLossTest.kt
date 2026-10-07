package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkLossTest {
    private val context = PeripheralContext(Identifier("AA:BB:CC:DD:EE:FF"))
    private val error = ConnectionLost("Remote disconnect")

    @AfterTest
    fun tearDown() {
        context.close()
    }

    private suspend fun connectToReady() {
        context.processEvent(ConnectionEvent.ConnectRequested)
        context.processEvent(ConnectionEvent.LinkEstablished)
        context.processEvent(ConnectionEvent.ServicesDiscovered)
        context.processEvent(ConnectionEvent.ConfigurationComplete)
    }

    @Test
    fun linkLossWhileReadyReachesDisconnected() =
        runTest {
            connectToReady()

            context.processLinkLoss(error)

            assertEquals(State.Disconnected.ByError(error), context.state.value)
        }

    @Test
    fun linkLossWhileConnectingReachesDisconnected() =
        runTest {
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.LinkEstablished)

            context.processLinkLoss(error)

            assertEquals(State.Disconnected.ByError(error), context.state.value)
        }

    @Test
    fun linkLossDuringRequestedDisconnectEndsByRequest() =
        runTest {
            connectToReady()
            context.processEvent(ConnectionEvent.DisconnectRequested)

            context.processLinkLoss(error)

            assertEquals(State.Disconnected.ByRequest, context.state.value)
        }

    @Test
    fun linkLossWhileDisconnectedLeavesStateAlone() =
        runTest {
            context.processLinkLoss(error)

            assertEquals(State.Disconnected.ByRequest, context.state.value)
        }

    @Test
    fun connectAfterLinkLossReachesReady() =
        runTest {
            connectToReady()
            context.processLinkLoss(error)

            connectToReady()

            assertEquals(State.Connected.Ready, context.state.value)
        }
}
