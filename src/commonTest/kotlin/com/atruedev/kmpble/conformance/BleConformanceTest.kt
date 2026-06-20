package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakeScannerBuilder
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Shared conformance test base for core BLE flows.
 *
 * Subclass in focused test classes (ScanConformanceTest,
 * ConnectionConformanceTest, GattConformanceTest, L2capConformanceTest,
 * PhyConformanceTest) which each own a subset of the test suite.
 *
 * Platform-specific runners live in jvmTest/iosTest and extend each
 * focused conformance class.
 *
 * ## Platform subclasses
 * ```
 * // jvmTest
 * class JvmScanConformanceTest : ScanConformanceTest()
 * class JvmConnectionConformanceTest : ConnectionConformanceTest()
 * class JvmGattConformanceTest : GattConformanceTest()
 * class JvmL2capConformanceTest : L2capConformanceTest()
 * class JvmPhyConformanceTest : PhyConformanceTest()
 *
 * // iosTest
 * class IosScanConformanceTest : ScanConformanceTest()
 * // ... same pattern for each focused conformance class
 * ```
 *
 * Override [buildScanner] or [buildPeripheral] to inject platform-specific
 * behavior variations.
 */
public abstract class BleConformanceTest {
    /** Factory for scanner instances. Override to inject platform behavior. */
    protected open fun buildScanner(block: FakeScannerBuilder.() -> Unit = {}): FakeScanner = FakeScanner(block)

    /**
     * Factory for peripheral instances. Override to inject platform behavior.
     *
     * @param observationDispatcher Optional dispatcher for observation flows.
     *   Pass a [kotlinx.coroutines.test.TestDispatcher] when tests need to
     *   control observation delivery timing with [kotlinx.coroutines.test.runCurrent].
     * @param block Builder DSL for configuring services and characteristics.
     */
    protected open fun buildPeripheral(
        observationDispatcher: CoroutineDispatcher? = null,
        block: FakePeripheralBuilder.() -> Unit = {},
    ): FakePeripheral {
        val builder = FakePeripheralBuilder()
        if (observationDispatcher != null) {
            builder.observationDispatcher(observationDispatcher)
        }
        return builder.apply(block).build()
    }
}
