package com.atruedev.kmpble.conformance

/**
 * iOS-specific GATT write-type conformance test runner.
 *
 * Inherits all [GattWriteTypeConformanceTest] test cases and runs them
 * with the default [FakeScanner]/[FakePeripheralBuilder] factories.
 * Run: ./gradlew :iosTest
 */
public class IosGattWriteTypeConformanceTest : GattWriteTypeConformanceTest()
