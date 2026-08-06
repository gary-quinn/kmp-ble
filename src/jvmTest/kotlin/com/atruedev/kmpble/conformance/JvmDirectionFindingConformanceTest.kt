package com.atruedev.kmpble.conformance

/**
 * JVM Direction Finding conformance test runner.
 *
 * Inherits all [DirectionFindingConformanceTest] test cases and runs them
 * with the JVM platform implementation.
 * Run: ./gradlew :jvmTest --tests "*DirectionFindingConformance*"
 */
public class JvmDirectionFindingConformanceTest : DirectionFindingConformanceTest()
