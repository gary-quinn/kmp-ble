package com.atruedev.kmpble.conformance

/**
 * JVM-specific GATT server conformance test runner.
 *
 * Inherits all [GattServerConformanceTest] test cases and runs them
 * with the default [GattServer] factory.
 * Run: ./gradlew :jvmTest --tests "*JvmGattServerConformance*"
 */
public class JvmGattServerConformanceTest : GattServerConformanceTest()
