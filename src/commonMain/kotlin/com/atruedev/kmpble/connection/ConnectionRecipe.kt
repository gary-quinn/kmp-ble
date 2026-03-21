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
 * // Connect to a heart rate monitor with medical-grade settings
 * peripheral.connect(ConnectionRecipe.MEDICAL)
 *
 * // Use fitness preset but override MTU
 * peripheral.connect(ConnectionRecipe.FITNESS.copy(mtuRequest = 512))
 * ```
 *
 * Parameter rationale:
 * ```
 * Preset    │ MTU │ Reconnect             │ Timeout │ Rationale
 * ──────────┼─────┼───────────────────────┼─────────┼──────────────────────────
 * MEDICAL   │ 247 │ Exp 1s-30s, 10 att    │ 60s     │ Continuous monitoring, battery
 * FITNESS   │ 247 │ Exp 500ms-15s, 5 att  │ 30s     │ Fast reconnect for workouts
 * IOT       │ nil │ Linear 2s, 3 att      │ 15s     │ Constrained devices, minimal MTU
 * CONSUMER  │ 247 │ Exp 1s-10s, 3 att     │ 20s     │ Fast first connect, moderate retry
 * ```
 */
public object ConnectionRecipe {

    /**
     * Medical / health monitoring devices (heart rate monitors, glucose sensors,
     * blood pressure cuffs, pulse oximeters).
     *
     * Optimized for continuous background monitoring:
     * - High MTU for efficient data transfer
     * - Aggressive reconnection (10 attempts) — data gaps are unacceptable
     * - Long timeout — medical devices can be slow to respond (bonding, encryption)
     */
    public val MEDICAL: ConnectionOptions = ConnectionOptions(
        mtuRequest = 247,
        timeout = 60.seconds,
        reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
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
    public val FITNESS: ConnectionOptions = ConnectionOptions(
        mtuRequest = 247,
        timeout = 30.seconds,
        reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
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
    public val IOT: ConnectionOptions = ConnectionOptions(
        mtuRequest = null,
        timeout = 15.seconds,
        reconnectionStrategy = ReconnectionStrategy.LinearBackoff(
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
    public val CONSUMER: ConnectionOptions = ConnectionOptions(
        mtuRequest = 247,
        timeout = 20.seconds,
        reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 10.seconds,
            maxAttempts = 3,
        ),
    )
}
