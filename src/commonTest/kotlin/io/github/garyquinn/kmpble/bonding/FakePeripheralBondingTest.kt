package io.github.garyquinn.kmpble.bonding

import io.github.garyquinn.kmpble.connection.State
import io.github.garyquinn.kmpble.connection.internal.ConnectionEvent
import io.github.garyquinn.kmpble.error.BleError
import io.github.garyquinn.kmpble.error.ConnectionFailed
import io.github.garyquinn.kmpble.error.ConnectionLost
import io.github.garyquinn.kmpble.error.GattError
import io.github.garyquinn.kmpble.error.OperationFailed
import io.github.garyquinn.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class FakePeripheralBondingTest {

    @Test
    fun bondStateDefaultsToUnknown() = runTest {
        val peripheral = FakePeripheral {}
        assertIs<BondState.Unknown>(peripheral.bondState.value)
    }

    @Test
    fun simulateExplicitBonding() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") { properties(read = true) }
            }
        }

        peripheral.simulateEvent(ConnectionEvent.ConnectRequested)
        assertIs<State.Connecting.Transport>(peripheral.state.value)

        peripheral.simulateEvent(ConnectionEvent.BondRequired)
        assertIs<State.Connecting.Authenticating>(peripheral.state.value)

        peripheral.simulateEvent(ConnectionEvent.BondSucceeded)
        assertIs<State.Connecting.Discovering>(peripheral.state.value)
    }

    @Test
    fun simulateBondFailure() = runTest {
        val peripheral = FakePeripheral {}

        peripheral.simulateEvent(ConnectionEvent.ConnectRequested)
        peripheral.simulateEvent(ConnectionEvent.BondRequired)

        peripheral.simulateEvent(
            ConnectionEvent.BondFailed(ConnectionFailed("User rejected pairing"))
        )
        assertIs<State.Disconnected.ByError>(peripheral.state.value)
    }

    @Test
    fun simulateMidConnectionBondChange() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {
                characteristic("2a37") { properties(read = true) }
            }
        }
        peripheral.connect()
        assertIs<State.Connected.Ready>(peripheral.state.value)

        peripheral.simulateEvent(ConnectionEvent.BondStateChanged)
        assertIs<State.Connected.BondingChange>(peripheral.state.value)

        peripheral.simulateEvent(ConnectionEvent.BondChangeProcessed)
        assertIs<State.Connected.Ready>(peripheral.state.value)
    }
}
