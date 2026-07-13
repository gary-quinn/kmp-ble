package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for [LifecycleSlots]'s re-entrancy guards on the connect/discovery/disconnect slots. */
class LifecycleSlotsTest {
    // -- armConnect --

    @Test
    fun armConnect_throwsWhenAlreadyArmed() {
        val slots = LifecycleSlots()
        slots.armConnect()

        val exception = assertFailsWith<IllegalStateException> { slots.armConnect() }
        assertEquals("connect() is already in progress on this peripheral", exception.message)
    }

    @Test
    fun armConnect_succeedsAgainAfterComplete() {
        val slots = LifecycleSlots()
        val first = slots.armConnect()
        slots.completeConnect()
        assertTrue(first.isCompleted)

        slots.armConnect()
    }

    @Test
    fun armConnect_succeedsAgainAfterClear() {
        val slots = LifecycleSlots()
        slots.armConnect()
        slots.clearConnect()

        slots.armConnect()
    }

    // -- armDiscovery --

    @Test
    fun armDiscovery_throwsWhenAlreadyArmed() {
        val slots = LifecycleSlots()
        slots.armDiscovery()

        val exception = assertFailsWith<IllegalStateException> { slots.armDiscovery() }
        assertEquals("service discovery is already in progress", exception.message)
    }

    @Test
    fun armDiscovery_succeedsAgainAfterCompletion() {
        val slots = LifecycleSlots()
        val first = slots.armDiscovery()
        slots.completeDiscovery(emptyList<DiscoveredService>())
        assertTrue(first.isCompleted)

        slots.armDiscovery()
    }

    @Test
    fun armDiscovery_succeedsAgainAfterFailure() {
        val slots = LifecycleSlots()
        val first = slots.armDiscovery()
        slots.failDiscovery(BleException(OperationFailed("discovery failed")))
        assertTrue(first.isCompleted)

        slots.armDiscovery()
    }

    @Test
    fun armDiscovery_succeedsAgainAfterClear() {
        val slots = LifecycleSlots()
        slots.armDiscovery()
        slots.clearDiscovery()

        slots.armDiscovery()
    }

    // -- tryArmDiscovery --

    @Test
    fun tryArmDiscovery_returnsTrueWhenNotArmed() {
        val slots = LifecycleSlots()
        assertTrue(slots.tryArmDiscovery())
    }

    @Test
    fun tryArmDiscovery_returnsFalseWithoutThrowingWhenAlreadyArmed() {
        val slots = LifecycleSlots()
        slots.armDiscovery()

        assertFalse(slots.tryArmDiscovery())
    }

    @Test
    fun tryArmDiscovery_returnsTrueAgainAfterCompletion() {
        val slots = LifecycleSlots()
        slots.tryArmDiscovery()
        slots.completeDiscovery(emptyList<DiscoveredService>())

        assertTrue(slots.tryArmDiscovery())
    }

    @Test
    fun tryArmDiscovery_andArmDiscovery_shareTheSameSlot() {
        val slots = LifecycleSlots()
        slots.tryArmDiscovery()

        assertFailsWith<IllegalStateException> { slots.armDiscovery() }
    }

    // -- armDisconnect --

    @Test
    fun armDisconnect_throwsWhenAlreadyArmed() {
        val slots = LifecycleSlots()
        slots.armDisconnect()

        val exception = assertFailsWith<IllegalStateException> { slots.armDisconnect() }
        assertEquals("disconnect() is already in progress on this peripheral", exception.message)
    }

    @Test
    fun armDisconnect_succeedsAgainAfterComplete() {
        val slots = LifecycleSlots()
        val first = slots.armDisconnect()
        slots.completeDisconnect()
        assertTrue(first.isCompleted)

        slots.armDisconnect()
    }

    @Test
    fun armDisconnect_succeedsAgainAfterClear() {
        val slots = LifecycleSlots()
        slots.armDisconnect()
        slots.clearDisconnect()

        slots.armDisconnect()
    }

    // -- the three slots don't share state --

    @Test
    fun connectDiscoveryAndDisconnect_areIndependentSlots() {
        val slots = LifecycleSlots()

        slots.armConnect()
        assertTrue(slots.tryArmDiscovery())
        // Real usage never disconnects mid-connect, but LifecycleSlots has no cross-slot
        // coupling - each guard only checks its own slot, so this must not throw.
        slots.armDisconnect()
    }

    // -- completing/failing/clearing a slot that was never armed is a no-op, not a crash --

    @Test
    fun completeDiscovery_onUnarmedSlot_doesNotThrow() {
        val slots = LifecycleSlots()
        slots.completeDiscovery(emptyList<DiscoveredService>())
    }

    @Test
    fun failDiscovery_onUnarmedSlot_doesNotThrow() {
        val slots = LifecycleSlots()
        slots.failDiscovery(BleException(OperationFailed("no discovery in flight")))
    }

    @Test
    fun completeConnect_onUnarmedSlot_doesNotThrow() {
        val slots = LifecycleSlots()
        slots.completeConnect()
    }

    @Test
    fun completeDisconnect_onUnarmedSlot_doesNotThrow() {
        val slots = LifecycleSlots()
        slots.completeDisconnect()
    }

    @Test
    fun clearDiscovery_isIdempotent() {
        val slots = LifecycleSlots()
        slots.armDiscovery()
        slots.clearDiscovery()
        slots.clearDiscovery()

        slots.armDiscovery()
    }
}
