package com.atruedev.kmpble.internal

import platform.Foundation.NSError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralDelegateStateTest {
    @Test
    fun losingDuplicateRegistrationDoesNotClobberSurvivor() {
        val state = CentralDelegateState()
        val id = "peripheral-1"

        var loserInvoked = false
        val loserCallback: (Boolean, NSError?) -> Unit = { _, _ -> loserInvoked = true }

        var survivorInvoked = false
        val survivorCallback: (Boolean, NSError?) -> Unit = { _, _ -> survivorInvoked = true }

        // Simulates PeripheralRegistry.getOrCreate() constructing two IosPeripheral
        // instances for the same identifier before its CAS resolves: both register in
        // their init block, so whichever registers last wins the shared map slot
        // regardless of which instance actually wins the CAS and survives.
        state.registerConnectionCallback(id, loserCallback)
        state.registerConnectionCallback(id, survivorCallback)

        // The loser discovers it lost the race and closes itself. Unregistering with its
        // own callback reference must not remove the survivor's live registration.
        state.unregisterConnectionCallback(id, loserCallback)

        state.handleConnectionFailure(id, null)

        assertTrue(survivorInvoked, "survivor's callback should still be registered")
        assertFalse(loserInvoked, "loser's callback must not fire")
    }

    @Test
    fun unregisterConnectionCallbackRemovesItsOwnRegistration() {
        val state = CentralDelegateState()
        val id = "peripheral-2"
        var invoked = false
        val callback: (Boolean, NSError?) -> Unit = { _, _ -> invoked = true }

        state.registerConnectionCallback(id, callback)
        state.unregisterConnectionCallback(id, callback)

        state.handleConnectionFailure(id, null)

        assertFalse(invoked)
    }

    @Test
    fun unregisterConnectionCallbackIgnoresStaleCallbackAfterReRegistration() {
        val state = CentralDelegateState()
        val id = "peripheral-3"

        var firstInvoked = false
        val first: (Boolean, NSError?) -> Unit = { _, _ -> firstInvoked = true }

        var secondInvoked = false
        val second: (Boolean, NSError?) -> Unit = { _, _ -> secondInvoked = true }

        // Mirrors IosPeripheral.connectInternal() re-affirming its own registration
        // before every connect, the same way ApplePeripheralBridge re-affirms
        // cbPeripheral.delegate before every native operation.
        state.registerConnectionCallback(id, first)
        state.registerConnectionCallback(id, second)

        // A stale unregister call carrying the pre-re-affirmation reference (e.g. from a
        // discarded duplicate that closes late) must not remove the re-affirmed one.
        state.unregisterConnectionCallback(id, first)

        state.handleConnectionFailure(id, null)

        assertTrue(secondInvoked)
        assertFalse(firstInvoked)
    }
}
