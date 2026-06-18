package com.atruedev.kmpble.server

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.testing.FakeExtendedAdvertiser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalBleApi::class)
class FakeExtendedAdvertiserTest {
    @Test
    fun startAndStopAdvertisingSet() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            val config = ExtendedAdvertiseConfig(name = "Test")

            val setId = advertiser.startAdvertisingSet(config)
            assertTrue(setId in advertiser.activeSets.value)
            assertEquals(config, advertiser.getConfig(setId))

            advertiser.stopAdvertisingSet(setId)
            assertTrue(advertiser.activeSets.value.isEmpty())
        }

    @Test
    fun multipleConcurrentSets() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            val id1 = advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(name = "Set1"))
            val id2 = advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(name = "Set2"))

            assertNotEquals(id1, id2)
            assertEquals(2, advertiser.activeSets.value.size)
            assertEquals("Set1", advertiser.getConfig(id1)?.name)
            assertEquals("Set2", advertiser.getConfig(id2)?.name)

            advertiser.stopAdvertisingSet(id1)
            assertEquals(setOf(id2), advertiser.activeSets.value)
        }

    @Test
    fun closeStopsAllSets() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(name = "A"))
            advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(name = "B"))

            advertiser.close()
            assertTrue(advertiser.activeSets.value.isEmpty())
        }

    @Test
    fun configPreservesPhyFields() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            val config =
                ExtendedAdvertiseConfig(
                    primaryPhy = Phy.LeCoded,
                    secondaryPhy = Phy.Le2M,
                    interval = AdvertiseInterval.LowLatency,
                )
            val setId = advertiser.startAdvertisingSet(config)
            val stored = advertiser.getConfig(setId)!!
            assertEquals(Phy.LeCoded, stored.primaryPhy)
            assertEquals(Phy.Le2M, stored.secondaryPhy)
            assertEquals(AdvertiseInterval.LowLatency, stored.interval)
        }

    @Test
    fun startAfterCloseThrows() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            advertiser.close()
            assertFailsWith<IllegalStateException> {
                advertiser.startAdvertisingSet(ExtendedAdvertiseConfig())
            }
        }

    @Test
    fun stopNonexistentSetIsSafe() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            advertiser.stopAdvertisingSet(999)
        }

    @Test
    fun periodicAdvertisingParametersStored() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            val periodic =
                PeriodicAdvertisingParameters(
                    includeTxPower = true,
                    interval = AdvertiseInterval.LowLatency,
                )
            val config =
                ExtendedAdvertiseConfig(
                    name = "PeriodicDevice",
                    periodicAdvertising = periodic,
                )

            val setId = advertiser.startAdvertisingSet(config)
            assertTrue(advertiser.isPeriodicAdvertisingActive(setId))

            val stored = advertiser.getPeriodicConfig(setId)
            assertNotNull(stored)
            assertEquals(true, stored.includeTxPower)
            assertEquals(AdvertiseInterval.LowLatency, stored.interval)
        }

    @Test
    fun periodicAdvertisingNullByDefault() =
        runTest {
            val advertiser = FakeExtendedAdvertiser()
            val config = ExtendedAdvertiseConfig(name = "NoPeriodic")

            val setId = advertiser.startAdvertisingSet(config)
            assertTrue(!advertiser.isPeriodicAdvertisingActive(setId))
            assertNull(advertiser.getPeriodicConfig(setId))
        }

    @Test
    fun configEqualityIncludesPeriodicAdvertising() =
        runTest {
            val periodic = PeriodicAdvertisingParameters(interval = AdvertiseInterval.LowPower)
            val configA =
                ExtendedAdvertiseConfig(
                    name = "Test",
                    periodicAdvertising = periodic,
                )
            val configB =
                ExtendedAdvertiseConfig(
                    name = "Test",
                    periodicAdvertising = PeriodicAdvertisingParameters(interval = AdvertiseInterval.LowPower),
                )
            val configC = ExtendedAdvertiseConfig(name = "Test")

            assertEquals(configA, configB)
            assertNotEquals(configA, configC)
        }
}
