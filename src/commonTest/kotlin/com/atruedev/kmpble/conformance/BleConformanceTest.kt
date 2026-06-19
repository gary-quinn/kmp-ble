package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakeScannerBuilder

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

    /** Factory for peripheral builder. Override to inject platform behavior. */
    protected open fun buildPeripheral(block: FakePeripheralBuilder.() -> Unit = {}) =
        FakePeripheralBuilder().apply(block).build()
}
