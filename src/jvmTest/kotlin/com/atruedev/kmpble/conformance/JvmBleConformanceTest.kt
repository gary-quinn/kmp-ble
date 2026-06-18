package com.atruedev.kmpble.conformance

/**
 * JVM-specific conformance test runner.
 *
 * Inherits all [BleConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmBleConformance*"
 */
public class JvmBleConformanceTest : BleConformanceTest()
