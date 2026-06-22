package com.atruedev.kmpble.conformance

/**
 * iOS-specific GATT server conformance test runner.
 *
 * Inherits all [GattServerConformanceTest] test cases and runs them
 * with the iOS [GattServer] factory.
 * Run: ./gradlew :iosTest --tests "*IosGattServerConformance*"
 */
public class IosGattServerConformanceTest : GattServerConformanceTest()
