package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class PeripheralExtensionsTest {

    private fun createPeripheral(): FakePeripheral = FakePeripheral {
        service("180d") {
            characteristic("2a37") { properties(notify = true, read = true) }
            characteristic("2a38") { properties(read = true) }
        }
        service("180f") {
            characteristic("2a19") { properties(read = true, notify = true) }
        }
    }

    // --- dump() ---

    @Test
    fun dumpShowsPeripheralIdentifier() {
        val peripheral = createPeripheral()
        val output = peripheral.dump()
        assertContains(output, peripheral.identifier.value)
    }

    @Test
    fun dumpShowsStateWhenNotConnected() {
        val peripheral = createPeripheral()
        val output = peripheral.dump()
        assertContains(output, "ByRequest") // State.Disconnected.ByRequest is the initial state
    }

    @Test
    fun dumpShowsNoServicesWhenDisconnected() {
        val peripheral = createPeripheral()
        val output = peripheral.dump()
        assertContains(output, "no services discovered")
    }

    @Test
    fun dumpShowsServicesAfterConnect() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        val output = peripheral.dump()
        assertContains(output, "Service")
        assertContains(output, "Char")
        assertContains(output, "notify")
        assertContains(output, "read")

        peripheral.close()
    }

    @Test
    fun dumpTreeStructureIsCorrect() = runTest {
        val peripheral = createPeripheral()
        peripheral.connect()

        val output = peripheral.dump()
        // Should have tree characters
        assertTrue(output.contains("├──") || output.contains("└──"))

        peripheral.close()
    }

    // --- whenReady {} ---

    @Test
    fun whenReadyConnectsExecutesAndCloses() = runTest {
        val peripheral = createPeripheral()
        var blockExecuted = false

        peripheral.whenReady {
            assertIs<State.Connected.Ready>(state.value)
            blockExecuted = true
        }

        assertTrue(blockExecuted)
    }

    @Test
    fun whenReadyClosesOnException() = runTest {
        val peripheral = createPeripheral()

        assertFailsWith<IllegalStateException> {
            peripheral.whenReady {
                throw IllegalStateException("test error")
            }
        }
        // Peripheral should still be closed even after exception
    }
}
