package com.atruedev.kmpble.conformance

/**
 * JVM-specific GATT conformance test runner.
 *
 * Inherits all [GattConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmGattConformance*"
 */
public class JvmGattConformanceTest : GattConformanceTest()
