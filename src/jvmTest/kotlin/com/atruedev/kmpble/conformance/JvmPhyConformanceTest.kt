package com.atruedev.kmpble.conformance

/**
 * JVM-specific PHY conformance test runner.
 *
 * Inherits all [PhyConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmPhyConformance*"
 */
public class JvmPhyConformanceTest : PhyConformanceTest()
