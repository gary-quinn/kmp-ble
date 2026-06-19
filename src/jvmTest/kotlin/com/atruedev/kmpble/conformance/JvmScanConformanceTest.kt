package com.atruedev.kmpble.conformance

/**
 * JVM-specific scan conformance test runner.
 *
 * Inherits all [ScanConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmScanConformance*"
 */
public class JvmScanConformanceTest : ScanConformanceTest()
