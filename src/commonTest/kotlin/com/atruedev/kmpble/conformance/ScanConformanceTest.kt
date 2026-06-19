package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.scanner.ScanEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scan conformance tests.
 *
 * Verifies scanner behavior across KMP platforms: advertisement
 * emission, discovery flow, and event structure.
 */
public abstract class ScanConformanceTest : BleConformanceTest() {
    @Test
    fun `scan emits found event for advertising peripheral`() =
        runTest {
            val scanner =
                buildScanner {
                    advertisement {
                        identifier("AA:BB:CC:DD:EE:FF")
                        name("ConformanceDevice")
                        rssi(-50)
                        serviceUuids("180d")
                    }
                }

            val event =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .first()

            assertEquals("ConformanceDevice", event.advertisement.name)
            assertTrue(event.advertisement.rssi <= 0)
            scanner.close()
        }
}
