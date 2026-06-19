package com.atruedev.kmpble.conformance

/**
 * JVM-specific connection conformance test runner.
 *
 * Inherits all [ConnectionConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmConnectionConformance*"
 */
public class JvmConnectionConformanceTest : ConnectionConformanceTest()
