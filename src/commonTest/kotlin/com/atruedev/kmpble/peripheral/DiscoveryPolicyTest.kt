package com.atruedev.kmpble.peripheral

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Decision-table coverage for [DiscoveryPolicy], the pure logic behind the iOS discovery
 * cache-reuse and stale-callback guards. Runs on the JVM, so it executes in this repo's
 * default test suite without a Kotlin/Native toolchain.
 */
class DiscoveryPolicyTest {
    // -- canReuseServiceCache --

    @Test
    fun `cache is reusable only when complete`() {
        assertFalse(
            DiscoveryPolicy.canReuseServiceCache(
                validated = true,
                connectedAtCreation = true,
                servicesPresent = false,
                allServicesHaveCharacteristics = true,
            ),
        )
        assertFalse(
            DiscoveryPolicy.canReuseServiceCache(
                validated = true,
                connectedAtCreation = true,
                servicesPresent = true,
                allServicesHaveCharacteristics = false,
            ),
        )
    }

    @Test
    fun `validated cache is reusable when complete`() {
        assertTrue(
            DiscoveryPolicy.canReuseServiceCache(
                validated = true,
                connectedAtCreation = false,
                servicesPresent = true,
                allServicesHaveCharacteristics = true,
            ),
        )
    }

    @Test
    fun `unvalidated cache from a fresh wrapper is not reusable`() {
        // knownServicesValid = false on a fresh wrapper over a previously disconnected
        // peripheral: the table may predate an unbond/GATT change that produced no
        // didModifyServices, so first connect must re-discover.
        assertFalse(
            DiscoveryPolicy.canReuseServiceCache(
                validated = false,
                connectedAtCreation = false,
                servicesPresent = true,
                allServicesHaveCharacteristics = true,
            ),
        )
    }

    @Test
    fun `cache from an already-connected retrieved peripheral is reusable`() {
        // retrieveConnectedPeripheralsWithServices returns peripherals iOS populated for
        // the current connection - re-running native discovery on that table is what
        // crashed with the zombie '-[CBCharacteristic handleCharacteristicsDiscovered:]'
        // after an OS re-bond.
        assertTrue(
            DiscoveryPolicy.canReuseServiceCache(
                validated = false,
                connectedAtCreation = true,
                servicesPresent = true,
                allServicesHaveCharacteristics = true,
            ),
        )
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
