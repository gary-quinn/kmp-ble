package com.atruedev.kmpble.conformance

/**
 * JVM-specific GATT write-type conformance test runner.
 *
 * Inherits all [GattWriteTypeConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :jvmTest --tests "*JvmGattWriteTypeConformance*"
 */
public class JvmGattWriteTypeConformanceTest : GattWriteTypeConformanceTest()
