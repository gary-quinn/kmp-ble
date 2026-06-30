package com.atruedev.kmpble.connection

import com.atruedev.kmpble.peripheral.state.ConnectionStateMachine
import com.atruedev.kmpble.peripheral.state.ConnectionState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM-only test that uses sealed subclass reflection to verify [ConnectionStateMachine]
 * parent mappings cover all [ConnectionState.Disconnected] subtypes.
 *
 * KMP common lacks sealed reflection, so this runs on JVM only.
 * Adding a new [ConnectionState.Disconnected] subtype without updating
 * [ConnectionStateMachine.stateParentMap] will fail this test.
 */
class StateMachineReflectionTest {
    @Test
    fun everyDisconnectedSubtypeHasParentMapping() {
        val sealedSubclasses = ConnectionState.Disconnected::class.sealedSubclasses
        val mapped = ConnectionStateMachine.allParentMappings.keys

        assertTrue(sealedSubclasses.isNotEmpty(), "Sealed subclass reflection returned empty")

        for (subclass in sealedSubclasses) {
            assertTrue(
                subclass in mapped,
                "${subclass.simpleName} is a Disconnected subtype but missing from ConnectionStateMachine.stateParentMap",
            )
        }
    }
}
