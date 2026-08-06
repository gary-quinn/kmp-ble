package com.atruedev.kmpble.conformance

/**
 * iOS Direction Finding conformance test runner.
 *
 * Inherits all [DirectionFindingConformanceTest] test cases and runs them
 * with the iOS platform implementation.
 * Run: ./gradlew :iosSimulatorArm64Test --tests "*DirectionFindingConformance*"
 *
 * Note: iOS does not have public direction finding APIs, so this will
 * primarily test the NotSupported result path.
 */
public class IosDirectionFindingConformanceTest : DirectionFindingConformanceTest()
