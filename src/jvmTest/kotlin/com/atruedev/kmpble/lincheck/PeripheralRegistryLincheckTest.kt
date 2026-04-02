package com.atruedev.kmpble.lincheck

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.StressOptions
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Lincheck concurrency tests for [PeripheralRegistry]. */
class PeripheralRegistryLincheckTest {
    private val ids =
        arrayOf(
            Identifier("device-0"),
            Identifier("device-1"),
            Identifier("device-2"),
        )

    init {
        PeripheralRegistry.clear()
    }

    @Operation
    fun getOrCreate(index: Int): String {
        val id = ids[index % ids.size]
        return PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }.identifier.value
    }

    @Operation
    fun remove(index: Int) {
        PeripheralRegistry.remove(ids[index % ids.size])
    }

    @Operation
    fun identifiers(): Set<String> = PeripheralRegistry.identifiers()

    @Operation
    fun clear() = PeripheralRegistry.clear()

    @Test
    fun stressTest() =
        StressOptions()
            .iterations(50)
            .threads(3)
            .actorsPerThread(3)
            .sequentialSpecification(PeripheralRegistrySequential::class.java)
            .check(this::class)

    @Test
    fun modelCheckingTest() =
        ModelCheckingOptions()
            .iterations(50)
            .threads(3)
            .actorsPerThread(3)
            .sequentialSpecification(PeripheralRegistrySequential::class.java)
            .check(this::class)

    @Test
    fun getOrCreateReturnsSameInstanceForSameIdentifier() {
        PeripheralRegistry.clear()
        val id = Identifier("test-device")
        val first = PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }
        val second = PeripheralRegistry.getOrCreate(id) { StubPeripheral(id) }
        assertSame(first, second)
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

/** Sequential specification: atomic get-or-put. Lincheck verifies the CAS-based registry matches. */
class PeripheralRegistrySequential {
    private val map = HashMap<String, String>()

    fun getOrCreate(index: Int): String {
        val ids = arrayOf("device-0", "device-1", "device-2")
        val key = ids[index % ids.size]
        return map.getOrPut(key) { key }
    }

    fun remove(index: Int) {
        val ids = arrayOf("device-0", "device-1", "device-2")
        map.remove(ids[index % ids.size])
    }

    fun identifiers(): Set<String> = map.keys.toSet()

    fun clear() = map.clear()
}
