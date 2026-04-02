package com.atruedev.kmpble.logging

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BleLogConfigTest {
    private lateinit var context: PeripheralContext

    @BeforeTest
    fun setUp() {
        context = PeripheralContext(Identifier("AA:BB:CC:DD:EE:FF"))
    }

    @AfterTest
    fun tearDown() {
        context.close()
        BleLogConfig.strictMode = false
        BleLogConfig.logger = null
    }

    @Test
    fun strictModeThrowsOnInvalidTransition() =
        runTest {
            BleLogConfig.strictMode = true
            assertFailsWith<IllegalStateException> {
                context.processEvent(ConnectionEvent.ServicesDiscovered)
            }
        }

    @Test
    fun strictModeOffIgnoresInvalidTransition() =
        runTest {
            BleLogConfig.strictMode = false
            val state = context.processEvent(ConnectionEvent.ServicesDiscovered)
            assertIs<State.Disconnected>(state)
        }

    @Test
    fun strictModeLogsBeforeThrowing() =
        runTest {
            val events = mutableListOf<BleLogEvent>()
            BleLogConfig.logger = BleLogger { events += it }
            BleLogConfig.strictMode = true

            assertFailsWith<IllegalStateException> {
                context.processEvent(ConnectionEvent.ServicesDiscovered)
            }

            val error = events.filterIsInstance<BleLogEvent.Error>().firstOrNull()
            assertNotNull(error)
            assertTrue("Invalid state transition" in error.message)
        }
}
