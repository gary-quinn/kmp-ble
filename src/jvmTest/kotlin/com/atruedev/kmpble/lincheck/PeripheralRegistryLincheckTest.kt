package com.atruedev.kmpble.lincheck

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import org.jetbrains.lincheck.LincheckAssertionError
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.StressOptions
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.junit.Test

/**
 * Lincheck tests for [PeripheralRegistry].
 *
 * PeripheralRegistry uses @Volatile copy-on-write with a documented TOCTOU
 * race in [PeripheralRegistry.getOrCreate]. These tests prove the race exists
 * by expecting [LincheckAssertionError]. If the race is ever fixed (e.g., by
 * adding synchronization), these tests will start failing — remove the
 * `expected` annotation at that point.
 *
 * Sequential specification: [PeripheralRegistrySequential] models the
 * intended atomic get-or-put behavior.
 */
class PeripheralRegistryLincheckTest {
    private val ids = arrayOf(
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

    @Test(expected = LincheckAssertionError::class)
    fun stressTest() = StressOptions()
        .iterations(50)
        .threads(3)
        .actorsPerThread(3)
        .sequentialSpecification(PeripheralRegistrySequential::class.java)
        .check(this::class)

    @Test(expected = LincheckAssertionError::class)
    fun modelCheckingTest() = ModelCheckingOptions()
        .iterations(50)
        .threads(3)
        .actorsPerThread(3)
        .sequentialSpecification(PeripheralRegistrySequential::class.java)
        .check(this::class)
}

/**
 * Sequential specification: atomic get-or-put with a HashMap.
 * This is what a correctly synchronized PeripheralRegistry would behave like.
 */
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
