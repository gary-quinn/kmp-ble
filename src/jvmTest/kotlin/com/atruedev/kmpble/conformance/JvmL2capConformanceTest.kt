package com.atruedev.kmpble.conformance

/**
 * JVM-specific L2CAP conformance test runner.
 *
 * Inherits all [L2capConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmL2capConformance*"
 */
public class JvmL2capConformanceTest : L2capConformanceTest()
