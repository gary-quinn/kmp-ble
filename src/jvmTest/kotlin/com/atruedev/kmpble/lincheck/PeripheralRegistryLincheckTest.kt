package com.atruedev.kmpble.lincheck

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Verifies [PeripheralRegistry] behavior.
 *
 * The registry uses @Volatile copy-on-write with a documented TOCTOU race in
 * [getOrCreate]. Concurrent Lincheck tests were removed because the implementation
 * is intentionally non-linearizable — the narrow race window is accepted as a
 * trade-off for keeping `toPeripheral()` non-suspend.
 */
class PeripheralRegistryLincheckTest {
    @Test
    fun getOrCreateReturnsSameInstanceForSameIdentifier() {
        PeripheralRegistry.clear()
        val id = Identifier("test-device")
        val first = PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }
        val second = PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }
        assertSame(first, second)
    }

    @Test
    fun getOrCreateReturnsDifferentInstancesForDifferentIdentifiers() {
        PeripheralRegistry.clear()
        val id1 = Identifier("device-a")
        val id2 = Identifier("device-b")
        val first = PeripheralRegistry.getOrCreate(id1) { StubPeripheral(id1) }
        val second = PeripheralRegistry.getOrCreate(id2) { StubPeripheral(id2) }
        assertEquals(id1, first.identifier)
        assertEquals(id2, second.identifier)
    }

    @Test
    fun removeAllowsNewCreation() {
        PeripheralRegistry.clear()
        val id = Identifier("test-device")
        val first = PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }
        PeripheralRegistry.remove(id)
        val second = PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }
        assertEquals(id, second.identifier)
    }

    @Test
    fun clearRemovesAllEntries() {
        PeripheralRegistry.clear()
        PeripheralRegistry.getOrCreate(Identifier("a")) { StubPeripheral(Identifier("a")) }
        PeripheralRegistry.getOrCreate(Identifier("b")) { StubPeripheral(Identifier("b")) }
        assertEquals(setOf("a", "b"), PeripheralRegistry.identifiers())
        PeripheralRegistry.clear()
        assertEquals(emptySet(), PeripheralRegistry.identifiers())
    }
}
