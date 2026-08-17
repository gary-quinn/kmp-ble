package com.atruedev.kmpble.peripheral

/**
 * Pure decision helpers for the iOS discovery pipeline, extracted so the reconnect/cache
 * and stale-callback policy can be unit-tested without CoreBluetooth objects.
 *
 * All functions are deterministic over their inputs; the iOS bridge computes the inputs
 * from live CoreBluetooth state.
 */
internal object DiscoveryPolicy {
    /**
     * What the discovery pipeline should do for a connect, decided purely from cache state
     * so the branch logic is unit-testable on the JVM without CoreBluetooth objects.
     */
    internal sealed interface DiscoveryAction {
        /** The cached table is complete and vouched for; reuse it without a native call. */
        data object ReuseCache : DiscoveryAction

        /**
         * A retrieved/restored peripheral whose iOS auto-discovery is still in flight; seed a
         * cycle from the incomplete table and wait for `didDiscoverCharacteristicsForService`
         * callbacks. Never re-run `discoverServices(null)`.
         */
        data object SeedAndWait : DiscoveryAction

        /** A fresh connect with no usable cache; run a native discovery pass. */
        data object Rediscover : DiscoveryAction
    }

    /**
     * Pick the discovery action for a connect. [validated] is whether a completed discovery
     * cycle vouches for the table; [connectedAtCreation] whether the peripheral was already
     * connected when the wrapper was created; [servicesPresent]/[allServicesHaveCharacteristics]
     * describe whether the table is complete.
     */
    fun decideDiscoveryAction(
        validated: Boolean,
        connectedAtCreation: Boolean,
        servicesPresent: Boolean,
        allServicesHaveCharacteristics: Boolean,
    ): DiscoveryAction {
        val cacheComplete = servicesPresent && allServicesHaveCharacteristics
        return when {
            (validated || connectedAtCreation) && cacheComplete -> DiscoveryAction.ReuseCache
            connectedAtCreation && !cacheComplete -> DiscoveryAction.SeedAndWait
            else -> DiscoveryAction.Rediscover
        }
    }

    /**
     * Whether a didDiscoverServices callback belongs to the current discovery cycle.
     *
     * [callbackGeneration] is the generation the bridge associates with the callback at
     * delivery time; [currentGeneration] is bumped on every new cycle and every disconnect.
     * A callback is stale when its generation no longer matches (interrupted cycle, or
     * delivered while the link is down), when a cycle is already set up (duplicate
     * delivery for the same native call), or when the peripheral is disconnected.
     */
    fun acceptsDidDiscoverServices(
        callbackGeneration: Int,
        currentGeneration: Int,
        cycleAlreadyActive: Boolean,
        peripheralConnected: Boolean,
    ): Boolean =
        callbackGeneration == currentGeneration &&
            !cycleAlreadyActive &&
            peripheralConnected

    /**
     * Whether a didDiscoverCharacteristicsForService callback belongs to the current
     * discovery cycle. Same staleness rules as [acceptsDidDiscoverServices]; the caller
     * has already verified a cycle is active.
     */
    fun acceptsDidDiscoverCharacteristics(
        cycleGeneration: Int,
        currentGeneration: Int,
        peripheralConnected: Boolean,
    ): Boolean =
        cycleGeneration == currentGeneration &&
            peripheralConnected
}
