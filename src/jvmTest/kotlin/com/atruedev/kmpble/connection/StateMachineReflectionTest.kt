package com.atruedev.kmpble.connection

import com.atruedev.kmpble.connection.internal.StateMachine
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM-only test that uses sealed subclass reflection to verify [StateMachine]
 * parent mappings cover all [State.Disconnected] subtypes.
 *
 * KMP common lacks sealed reflection, so this runs on JVM only.
 * Adding a new [State.Disconnected] subtype without updating
 * [StateMachine.stateParentMap] will fail this test.
 */
class StateMachineReflectionTest {
    @Test
    fun everyDisconnectedSubtypeHasParentMapping() {
        val sealedSubclasses = State.Disconnected::class.sealedSubclasses.map { it }
        val mapped = StateMachine.allParentMappings.keys

        assertTrue(sealedSubclasses.isNotEmpty(), "Sealed subclass reflection returned empty")

        for (subclass in sealedSubclasses) {
            assertTrue(
                subclass in mapped,
                "${subclass.simpleName} is a Disconnected subtype but missing from StateMachine.stateParentMap",
            )
        }
    }
}
