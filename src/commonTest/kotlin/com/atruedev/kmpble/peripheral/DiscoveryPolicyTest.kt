package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.peripheral.DiscoveryPolicy.DiscoveryAction.Rediscover
import com.atruedev.kmpble.peripheral.DiscoveryPolicy.DiscoveryAction.ReuseCache
import com.atruedev.kmpble.peripheral.DiscoveryPolicy.DiscoveryAction.SeedAndWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Decision-table coverage for [DiscoveryPolicy], the pure logic behind the iOS discovery
 * cache-reuse, stale-callback, and retrieved-seed guards. Runs on the JVM, so it executes
 * in this repo's default test suite without a Kotlin/Native toolchain.
 */
class DiscoveryPolicyTest {
    private fun decide(
        validated: Boolean,
        connectedAtCreation: Boolean,
        servicesPresent: Boolean,
        allServicesHaveCharacteristics: Boolean,
    ): DiscoveryPolicy.DiscoveryAction =
        DiscoveryPolicy.decideDiscoveryAction(
            validated,
            connectedAtCreation,
            servicesPresent,
            allServicesHaveCharacteristics,
        )

    // -- decideDiscoveryAction --

    @Test
    fun `cache is reusable only when complete and vouched for`() {
        assertEquals(ReuseCache, decide(true, true, true, true))
        assertEquals(ReuseCache, decide(true, false, true, true))
        // Incomplete cache: never reuse.
        assertEquals(Rediscover, decide(true, false, true, false))
        assertEquals(SeedAndWait, decide(true, true, true, false))
        assertEquals(SeedAndWait, decide(true, true, false, false))
    }

    @Test
    fun `unvalidated cache from a fresh wrapper rediscovers`() {
        // knownServicesValid = false on a fresh wrapper over a previously disconnected
        // peripheral: the table may predate an unbond/GATT change that produced no
        // didModifyServices, so first connect must re-discover.
        assertEquals(Rediscover, decide(false, false, true, true))
    }

    @Test
    fun `cache from an already-connected retrieved peripheral is reusable`() {
        // retrieveConnectedPeripheralsWithServices returns peripherals iOS populated for
        // the current connection - re-running native discovery on that table is what
        // crashed with the zombie '-[CBCharacteristic handleCharacteristicsDiscovered:]'
        // after an OS re-bond.
        assertEquals(ReuseCache, decide(false, true, true, true))
    }

    @Test
    fun `retrieved peripheral with incomplete cache seeds instead of rediscovering`() {
        // connectedAtCreation + characteristics still nil: the crash case. Must seed
        // a cycle and wait, never call discoverServices(null).
        assertEquals(SeedAndWait, decide(false, true, true, false))
        // No services yet either (BT just came on) - still seed, still never rediscover.
        assertEquals(SeedAndWait, decide(false, true, false, false))
    }

    @Test
    fun `fresh connect never seeds regardless of cache completeness`() {
        // A wrapper over a disconnected peripheral must rediscover, not seed -- there is
        // no in-flight iOS auto-discovery to wait on.
        assertEquals(Rediscover, decide(false, false, true, false))
        assertEquals(Rediscover, decide(false, false, true, true))
    }

    // -- acceptsDidDiscoverServices --

    @Test
    fun `matching generation with no active cycle while connected is accepted`() {
        assertTrue(
            DiscoveryPolicy.acceptsDidDiscoverServices(
                callbackGeneration = 3,
                currentGeneration = 3,
                cycleAlreadyActive = false,
                peripheralConnected = true,
            ),
        )
    }

    @Test
    fun `stale generation is rejected`() {
        // Generation bumped on disconnect or by a newer cycle: the callback belongs to an
        // interrupted/superseded native call.
        assertFalse(
            DiscoveryPolicy.acceptsDidDiscoverServices(
                callbackGeneration = 1,
                currentGeneration = 3,
                cycleAlreadyActive = false,
                peripheralConnected = true,
            ),
        )
    }

    @Test
    fun `duplicate delivery with an active cycle is rejected`() {
        assertFalse(
            DiscoveryPolicy.acceptsDidDiscoverServices(
                callbackGeneration = 3,
                currentGeneration = 3,
                cycleAlreadyActive = true,
                peripheralConnected = true,
            ),
        )
    }

    @Test
    fun `callback while disconnected is rejected`() {
        // A didDiscoverServices delivered after the link dropped is stale: issuing
        // discoverCharacteristics on a dead connection would hold the discovery slot
        // forever and block the next connect.
        assertFalse(
            DiscoveryPolicy.acceptsDidDiscoverServices(
                callbackGeneration = 3,
                currentGeneration = 3,
                cycleAlreadyActive = false,
                peripheralConnected = false,
            ),
        )
    }

    // -- acceptsDidDiscoverCharacteristics --

    @Test
    fun `characteristics callback matching its cycle is accepted`() {
        assertTrue(
            DiscoveryPolicy.acceptsDidDiscoverCharacteristics(
                cycleGeneration = 3,
                currentGeneration = 3,
                peripheralConnected = true,
            ),
        )
    }

    @Test
    fun `characteristics callback from a superseded cycle is rejected`() {
        assertFalse(
            DiscoveryPolicy.acceptsDidDiscoverCharacteristics(
                cycleGeneration = 1,
                currentGeneration = 3,
                peripheralConnected = true,
            ),
        )
    }

    @Test
    fun `characteristics callback while disconnected is rejected`() {
        assertFalse(
            DiscoveryPolicy.acceptsDidDiscoverCharacteristics(
                cycleGeneration = 3,
                currentGeneration = 3,
                peripheralConnected = false,
            ),
        )
    }
}
