package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.testing.configurePhy
import com.atruedev.kmpble.testing.emitPhyUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PHY selection and update conformance tests.
 *
 * Verifies setPreferredPhy, readPhy, and phyUpdate flow across
 * KMP platforms. Uses [ExperimentalBleApi] for PHY operations.
 */
public abstract class PhyConformanceTest : BleConformanceTest() {
    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `setPreferredPhy returns PhyResult with requested PHY`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect(ConnectionOptions())
            val result = peripheral.setPreferredPhy(Phy.Le2M, Phy.Le2M)
            assertEquals(Phy.Le2M, result?.tx)
            assertEquals(Phy.Le2M, result?.rx)
            peripheral.close()
        }

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `readPhy returns configured PHY values`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.configurePhy(Phy.LeCoded, Phy.Le2M)
            peripheral.connect(ConnectionOptions())
            val result = peripheral.readPhy()
            assertEquals(Phy.LeCoded, result?.tx)
            assertEquals(Phy.Le2M, result?.rx)
            peripheral.close()
        }

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `phyUpdate flow receives emitted updates`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect(ConnectionOptions())
            val updates = mutableListOf<PhyUpdate>()
            val job = backgroundScope.launch { peripheral.phyUpdate.collect { updates.add(it) } }
            testScheduler.runCurrent()
            peripheral.emitPhyUpdate(Phy.Le2M, Phy.LeCoded)
            testScheduler.runCurrent()
            assertEquals(1, updates.size)
            assertEquals(Phy.Le2M, updates[0].txPhy)
            assertEquals(Phy.LeCoded, updates[0].rxPhy)
            job.cancel()
            peripheral.close()
        }

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `setPreferredPhy with LeCoded returns LeCoded`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect(ConnectionOptions())
            val result = peripheral.setPreferredPhy(Phy.LeCoded, Phy.LeCoded)
            assertEquals(Phy.LeCoded, result?.tx)
            assertEquals(Phy.LeCoded, result?.rx)
            peripheral.close()
        }
}
