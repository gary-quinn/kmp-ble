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
     * Whether the cached CBService/CBCharacteristic table can be reused without a native
     * discoverServices round trip.
     *
     * The cache is reusable only when it is complete ([servicesPresent] and
     * [allServicesHaveCharacteristics]) AND either a completed discovery cycle vouches for
     * it ([validated]) or the peripheral was already connected when the wrapper was created
     * ([connectedAtCreation]) - in the latter case iOS populated the table for the current
     * connection, so re-running native discovery on it is both unnecessary and unsafe.
     */
    fun canReuseServiceCache(
        validated: Boolean,
        connectedAtCreation: Boolean,
        servicesPresent: Boolean,
        allServicesHaveCharacteristics: Boolean,
    ): Boolean =
        (validated || connectedAtCreation) &&
            servicesPresent &&
            allServicesHaveCharacteristics

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
