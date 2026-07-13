package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.gatt.DiscoveredService
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the re-entrancy guard [LifecycleSlots] relies on to prevent overlapping
 * connect/discovery/disconnect cycles on a single peripheral.
 *
 * [LifecycleSlots.armDiscovery] is the guard `IosPeripheral.handleConnectionCallback`
 * uses to reject a duplicate "connected" callback from CoreBluetooth (e.g. the OS
 * auto-reconnecting an already-bonded peripheral while our own connect() is also
 * completing) instead of starting a second, overlapping discoverServices() cycle.
 */
class LifecycleSlotsTest {
    @Test
    fun armDiscovery_throwsWhenAlreadyArmed() {
        val slots = LifecycleSlots()
        slots.armDiscovery()

        assertFailsWith<IllegalStateException> {
            slots.armDiscovery()
        }
    }

    @Test
    fun armDiscovery_succeedsAgainAfterCompletion() {
        val slots = LifecycleSlots()
        val first = slots.armDiscovery()
        slots.completeDiscovery(emptyList<DiscoveredService>())
        assertTrue(first.isCompleted)

        // Should not throw: the slot was cleared by completeDiscovery().
        slots.armDiscovery()
    }

    @Test
    fun armDiscovery_succeedsAgainAfterFailure() {
        val slots = LifecycleSlots()
        val first = slots.armDiscovery()
        slots.failDiscovery(RuntimeException("discovery failed"))
        assertTrue(first.isCompleted)

        // Should not throw: the slot was cleared by failDiscovery().
        slots.armDiscovery()
    }

    @Test
    fun armDiscovery_succeedsAgainAfterClear() {
        val slots = LifecycleSlots()
        slots.armDiscovery()
        slots.clearDiscovery()

        // Should not throw: the slot was cleared by clearDiscovery().
        slots.armDiscovery()
    }

    @Test
    fun armConnect_andArmDiscovery_areIndependentSlots() {
        val slots = LifecycleSlots()
        slots.armConnect()

        // A connect already in flight must not block arming discovery - they are
        // independent lifecycle slots.
        slots.armDiscovery()
    }
}
