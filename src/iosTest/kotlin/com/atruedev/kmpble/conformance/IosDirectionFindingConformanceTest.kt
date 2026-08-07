package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.testing.FakePeripheral

/**
 * iOS Direction Finding conformance test runner.
 *
 * Inherits all [DirectionFindingConformanceTest] test cases and runs them
 * against the iOS platform implementation via a [FakePeripheral] configured
 * to simulate iOS CoreBluetooth behavior.
 *
 * iOS CoreBluetooth does not expose CTE (Constant Tone Extension) data to
 * applications, so the platform returns [DirectionFindingResult.NotSupported]
 * for every direction finding request. This runner verifies that the
 * NotSupported path behaves correctly on iOS.
 *
 * Run: ./gradlew :iosSimulatorArm64Test --tests "*DirectionFindingConformance*"
 */
public class IosDirectionFindingConformanceTest : DirectionFindingConformanceTest() {
    override fun buildPeripheral(): FakePeripheral =
        FakePeripheral {
            // iOS lacks public CTE APIs; always return NotSupported.
            onDirectionFinding { DirectionFindingResult.NotSupported }
        }
}
