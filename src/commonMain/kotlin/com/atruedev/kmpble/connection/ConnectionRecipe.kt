package com.atruedev.kmpble.connection

import kotlin.time.Duration.Companion.seconds

/**
 * Pre-built [ConnectionOptions] for common BLE device categories.
 *
 * Each preset provides optimized MTU, reconnection strategy, and timeout
 * for its target use case. Use these as starting points — override individual
 * fields with [ConnectionOptions.copy] if needed.
 *
 * ```
 * peripheral.connect(ConnectionRecipe.MEDICAL)
 * peripheral.connect(ConnectionRecipe.FITNESS.copy(mtuRequest = 512))
 * ```
 */
public object ConnectionRecipe {
    /** BLE 4.2 maximum ATT MTU — 251 bytes minus 4 bytes L2CAP header. */
    private const val BLE_4_2_MAX_ATT_MTU = 247

    /**
     * Medical / health monitoring devices (heart rate monitors, glucose sensors,
     * blood pressure cuffs, pulse oximeters).
     *
     * Optimized for continuous background monitoring:
     * - High MTU for efficient data transfer
     * - Aggressive reconnection (10 attempts) — data gaps are unacceptable
     * - Long timeout — medical devices can be slow to respond (bonding, encryption)
     */
    public val MEDICAL: ConnectionOptions =
        ConnectionOptions(
            mtuRequest = BLE_4_2_MAX_ATT_MTU,
            timeout = 60.seconds,
            gattOperationTimeout = 30.seconds,
            reconnectionStrategy =
                ReconnectionStrategy.ExponentialBackoff(
                    initialDelay = 1.seconds,
                    maxDelay = 30.seconds,
                    maxAttempts = 10,
                ),
        )

    /**
     * Fitness devices (activity trackers, cycling sensors, running pods,
     * fitness machines).
     *
     * Optimized for workout sessions:
     * - High MTU for sensor data throughput
     * - Fast reconnection — workout interruptions should be brief
     * - Moderate timeout — fitness devices respond reasonably fast
     */
    public val FITNESS: ConnectionOptions =
        ConnectionOptions(
            mtuRequest = BLE_4_2_MAX_ATT_MTU,
            timeout = 30.seconds,
            gattOperationTimeout = 10.seconds,
            reconnectionStrategy =
                ReconnectionStrategy.ExponentialBackoff(
                    initialDelay = 0.5.seconds,
                    maxDelay = 15.seconds,
                    maxAttempts = 5,
                ),
        )

    /**
     * Industrial IoT / constrained devices (sensors, actuators, beacons,
     * environmental monitors).
     *
     * Optimized for low-power constrained devices:
     * - Default MTU — many IoT devices don't support MTU negotiation
     * - Conservative reconnection — battery-constrained, don't hammer
     * - Short timeout — if it doesn't connect quickly, it's probably off
     */
    public val IOT: ConnectionOptions =
        ConnectionOptions(
            mtuRequest = null,
            timeout = 15.seconds,
            gattOperationTimeout = 10.seconds,
            reconnectionStrategy =
                ReconnectionStrategy.LinearBackoff(
                    delay = 2.seconds,
                    maxAttempts = 3,
                ),
        )

    /**
     * Consumer electronics (headphones, speakers, smart home, keyboards,
     * game controllers).
     *
     * Optimized for responsive first connection:
     * - High MTU for audio/HID data
     * - Moderate reconnection — users will retry manually if needed
     * - Short timeout — consumer devices should connect quickly
     */
    public val CONSUMER: ConnectionOptions =
        ConnectionOptions(
            mtuRequest = BLE_4_2_MAX_ATT_MTU,
            timeout = 20.seconds,
            gattOperationTimeout = 10.seconds,
            reconnectionStrategy =
                ReconnectionStrategy.ExponentialBackoff(
                    initialDelay = 1.seconds,
                    maxDelay = 10.seconds,
                    maxAttempts = 3,
                ),
        )
}
