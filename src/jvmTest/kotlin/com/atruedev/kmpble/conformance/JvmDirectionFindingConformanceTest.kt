package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.testing.FakePeripheral

/**
 * JVM Direction Finding conformance test runner.
 *
 * Inherits all [DirectionFindingConformanceTest] test cases and runs them
 * against the JVM platform implementation via a [FakePeripheral] configured
 * to simulate JVM's BLE direction finding behavior.
 *
 * JVM implementation supports full direction finding (AoA/AoD) via
 * simulated antenna switching and angle computation.
 */
public class JvmDirectionFindingConformanceTest : DirectionFindingConformanceTest() {
    override fun buildPeripheral(): FakePeripheral = FakePeripheral {
        onDirectionFinding {
            DirectionFindingResult.Angle(
                azimuth = 45.0f,
                elevation = 10.0f,
                signalQuality = -42.0f,
            )
        }
    }
}
