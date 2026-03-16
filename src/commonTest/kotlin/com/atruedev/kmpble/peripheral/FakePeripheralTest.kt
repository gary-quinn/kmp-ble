package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakePeripheralTest {

    private fun createPeripheral() = FakePeripheral {
        service("180d") {
            characteristic("2a37") { properties(notify = true) }
            characteristic("2a38") { properties(read = true) }
        }
        service("180a") {
            characteristic("2a29") { properties(read = true) }
        }
    }

    @Test
    fun initialStateIsDisconnected() = runTest {
        val peripheral = createPeripheral()
        assertIs<State.Disconnected>(peripheral.state.value)
    }

    @Test
    fun connectTransitionsToReady() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()
        assertIs<State.Connected.Ready>(peripheral.state.value)
    }

    @Test
    fun disconnectTransitionsToByRequest() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()
        peripheral.disconnect()
        assertIs<State.Disconnected.ByRequest>(peripheral.state.value)
    }

    @Test
    fun servicesAvailableAfterConnect() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        val services = peripheral.services.value
        assertNotNull(services)
        assertEquals(2, services.size)
        assertEquals(uuidFrom("180d"), services[0].uuid)
        assertEquals(2, services[0].characteristics.size)
    }

    @Test
    fun servicesClearedAfterDisconnect() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()
        assertNotNull(peripheral.services.value)

        peripheral.disconnect()
        assertNull(peripheral.services.value)
    }

    @Test
    fun findCharacteristicWorks() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        val char = peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))
        assertNotNull(char)
        assertEquals(true, char.properties.notify)
    }

    @Test
    fun findCharacteristicReturnsNullWhenNotFound() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        assertNull(peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("ffff")))
        assertNull(peripheral.findCharacteristic(uuidFrom("ffff"), uuidFrom("2a37")))
    }

    @Test
    fun findCharacteristicReturnsNullBeforeConnect() = runTest {
        val peripheral = createPeripheral()
        assertNull(peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37")))
    }

    @Test
    fun closePreventsFurtherUse() = runTest {
        val peripheral = createPeripheral()
        peripheral.close()

        assertFailsWith<IllegalStateException> {
            peripheral.connect()
        }
    }

    @Test
    fun connectionFailureTransitionsToByError() = runTest {
        val peripheral = FakePeripheral {
            onConnect { Result.failure(Exception("Timeout")) }
        }

        peripheral.connect()
        assertIs<State.Disconnected.ByError>(peripheral.state.value)
    }

    @Test
    fun reconnectAfterDisconnect() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()
        peripheral.disconnect()
        peripheral.connect()
        assertIs<State.Connected.Ready>(peripheral.state.value)
        assertNotNull(peripheral.services.value)
    }

    // --- simulateEvent: test intermediate states ---

    @Test
    fun simulateEventAllowsIntermediateStateObservation() = runTest {
        val peripheral = createPeripheral()

        val s1 = peripheral.simulateEvent(ConnectionEvent.ConnectRequested)
        assertIs<State.Connecting.Transport>(s1)
        assertIs<State.Connecting.Transport>(peripheral.state.value)

        val s2 = peripheral.simulateEvent(ConnectionEvent.LinkEstablished)
        assertIs<State.Connecting.Discovering>(s2)

        val s3 = peripheral.simulateEvent(ConnectionEvent.ServicesDiscovered)
        assertIs<State.Connecting.Configuring>(s3)

        val s4 = peripheral.simulateEvent(ConnectionEvent.ConfigurationComplete)
        assertIs<State.Connected.Ready>(s4)
    }

    @Test
    fun simulateConnectionLostFromReady() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        val error = ConnectionLost("link supervision timeout")
        val s = peripheral.simulateEvent(ConnectionEvent.ConnectionLost(error))
        assertIs<State.Disconnecting.Error>(s)
    }

    @Test
    fun simulateAdapterOff() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        val s = peripheral.simulateEvent(ConnectionEvent.AdapterOff)
        assertIs<State.Disconnected.BySystemEvent>(s)
    }
}
