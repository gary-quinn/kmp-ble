package com.atruedev.kmpble.conformance

/**
 * JVM runner for [ConnectionSubratingConformanceTest].
 *
 * Verifies subrating parameter validation, result types, and FakePeripheral
 * behavior using the default test harness.
 *
 * Run: ./gradlew :jvmTest --tests "*JvmConnectionSubratingConformance*"
 */
public class JvmConnectionSubratingConformanceTest : ConnectionSubratingConformanceTest()
